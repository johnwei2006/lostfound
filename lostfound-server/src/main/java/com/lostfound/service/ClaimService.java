package com.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lostfound.auth.UserContext;
import com.lostfound.common.BusinessException;
import com.lostfound.common.PageResult;
import com.lostfound.dto.ClaimDTO;
import com.lostfound.entity.ClaimRequest;
import com.lostfound.entity.Found;
import com.lostfound.entity.Lost;
import com.lostfound.entity.User;
import com.lostfound.mapper.ClaimRequestMapper;
import com.lostfound.mapper.FoundMapper;
import com.lostfound.mapper.UserMapper;
import com.lostfound.vo.ClaimVO;
import com.lostfound.vo.ItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 认领流转服务：
 * 失主对拾物提交认领申请 → 发布者 / 站点管理员 / 全局 ADMIN 审核 → 通过后物品流转完成。
 */
@Service
@RequiredArgsConstructor
public class ClaimService {

    private final ClaimRequestMapper claimRequestMapper;
    private final ItemService itemService;
    private final MessageService messageService;
    private final UserMapper userMapper;
    private final FoundMapper foundMapper;
    private final SitePermissionService sitePermission;
    private final ReturnService returnService;

    @Transactional
    public Long submit(String type, Long itemId, Long userId, ClaimDTO dto) {
        if ("FOUND".equals(type)) {
            Found f = itemService.requireFound(itemId);
            if (Objects.equals(f.getUserId(), userId)) {
                throw new BusinessException("不能认领自己发布的信息");
            }
            if (!Found.STATUS_WAITING.equals(f.getStatus())) {
                throw new BusinessException("该物品当前状态不可认领");
            }
        } else {
            Lost l = itemService.requireLost(itemId);
            if (Objects.equals(l.getUserId(), userId)) {
                throw new BusinessException("不能对自己发布的信息提交申请");
            }
            if (!Lost.STATUS_WAITING.equals(l.getStatus())) {
                throw new BusinessException("该信息当前状态不可提交申请");
            }
        }

        // 防重复申请
        Long exists = claimRequestMapper.selectCount(new QueryWrapper<ClaimRequest>()
                .eq("item_type", type).eq("item_id", itemId)
                .eq("user_id", userId).eq("status", ClaimRequest.STATUS_PENDING));
        if (exists > 0) {
            throw new BusinessException("已提交过申请，请等待处理");
        }

        ClaimRequest c = new ClaimRequest();
        c.setItemType(type);
        c.setItemId(itemId);
        c.setUserId(userId);
        c.setDescription(dto.getDescription());
        c.setContact(dto.getContact());
        c.setStatus(ClaimRequest.STATUS_PENDING);
        c.setCreateTime(LocalDateTime.now());
        c.setUpdateTime(LocalDateTime.now());
        claimRequestMapper.insert(c);

        // 通知发布者
        Long ownerId = "FOUND".equals(type)
                ? itemService.requireFound(itemId).getUserId()
                : itemService.requireLost(itemId).getUserId();
        messageService.send(ownerId, "收到新的认领申请",
                "您发布的信息收到了新的认领申请，请及时审核处理。", "CLAIM", itemId);
        return c.getId();
    }

    /** 某物品的申请列表（仅发布者/管理员可见） */
    public List<ClaimVO> listByItem(String type, Long itemId, Long userId, boolean isAdmin) {
        Long ownerId = "FOUND".equals(type)
                ? itemService.requireFound(itemId).getUserId()
                : itemService.requireLost(itemId).getUserId();
        if (!Objects.equals(ownerId, userId) && !isAdmin) {
            throw new BusinessException(403, "仅发布者可查看认领申请");
        }
        List<ClaimRequest> list = claimRequestMapper.selectList(new QueryWrapper<ClaimRequest>()
                .eq("item_type", type).eq("item_id", itemId).orderByDesc("create_time"));
        return toVO(list);
    }

    /** 我提交的申请 */
    public List<ClaimVO> mine(Long userId) {
        return toVO(claimRequestMapper.selectList(new QueryWrapper<ClaimRequest>()
                .eq("user_id", userId).orderByDesc("create_time")));
    }

    /** 站点管理后台的认领申请队列（不触发物品浏览量自增） */
    public PageResult<ClaimVO> listForSite(Long siteId, String status, long page, long size) {
        sitePermission.requireManager(siteId);
        List<Long> itemIds = foundMapper.selectList(new QueryWrapper<Found>()
                        .select("id").eq("site_id", siteId))
                .stream().map(Found::getId).toList();
        PageResult<ClaimVO> result = new PageResult<>();
        result.setTotal(0);
        result.setPages(0);
        result.setCurrent(page);
        result.setSize(size);
        result.setRecords(List.of());
        if (itemIds.isEmpty()) return result;

        QueryWrapper<ClaimRequest> qw = new QueryWrapper<ClaimRequest>()
                .eq("item_type", ClaimRequest.TYPE_FOUND).in("item_id", itemIds);
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            qw.eq("status", status.trim().toUpperCase());
        }
        qw.orderByDesc("create_time");
        Page<ClaimRequest> p = claimRequestMapper.selectPage(new Page<>(page, size), qw);
        result.setTotal(p.getTotal());
        result.setPages(p.getPages());
        result.setRecords(toSimpleVO(p.getRecords()));
        return result;
    }

    /** 审核申请：approve=true 则物品流转完成，并统一写 found_return */
    @Transactional
    public void review(Long claimId, Long reviewerId, boolean approve) {
        ClaimRequest c = claimRequestMapper.selectById(claimId);
        if (c == null) throw new BusinessException("申请不存在");
        if (!ClaimRequest.STATUS_PENDING.equals(c.getStatus())) {
            throw new BusinessException("该申请已处理");
        }
        Long ownerId = ClaimRequest.TYPE_FOUND.equals(c.getItemType())
                ? itemService.requireFound(c.getItemId()).getUserId()
                : itemService.requireLost(c.getItemId()).getUserId();

        boolean allowed = Objects.equals(ownerId, reviewerId) || UserContext.isAdmin();
        if (!allowed && ClaimRequest.TYPE_FOUND.equals(c.getItemType())) {
            Found f = foundMapper.selectById(c.getItemId());
            allowed = f != null && f.getSiteId() != null && sitePermission.canManage(f.getSiteId());
        }
        if (!allowed) {
            throw new BusinessException(403, "仅发布者或该招领站管理员可审核认领申请");
        }

        c.setStatus(approve ? ClaimRequest.STATUS_APPROVED : ClaimRequest.STATUS_REJECTED);
        c.setUpdateTime(LocalDateTime.now());
        claimRequestMapper.updateById(c);

        if (approve) {
            itemService.changeStatus(c.getItemType(), c.getItemId(), reviewerId, true, "FINISHED");
            returnService.recordClaimReturn(c, reviewerId);
        }

        String typeText = ClaimRequest.TYPE_FOUND.equals(c.getItemType()) ? "拾物" : "失物";
        messageService.send(c.getUserId(),
                approve ? "认领申请已通过" : "认领申请被拒绝",
                approve ? "恭喜！您的认领申请已通过审核，请联系发布者完成交接。"
                        : "很抱歉，您的认领申请未通过审核，可补充凭证后重新申请。",
                "CLAIM", c.getItemId());
        if (approve) {
            messageService.send(ownerId, typeText + "已确认归还", "认领申请审核通过，物品已标记为已完成。", "CLAIM", c.getItemId());
        }
    }

    private List<ClaimVO> toVO(List<ClaimRequest> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, User> users = userMapper.selectBatchIds(
                        list.stream().map(ClaimRequest::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return list.stream().map(c -> {
            ClaimVO vo = new ClaimVO();
            vo.setId(c.getId());
            vo.setItemType(c.getItemType());
            vo.setItemId(c.getItemId());
            try {
                ItemVO item = itemService.detail(c.getItemType(), c.getItemId(), null);
                vo.setItemTitle(item.getTitle());
            } catch (Exception ignored) {
                // 物品可能已被删除
            }
            vo.setUserId(c.getUserId());
            User u = users.get(c.getUserId());
            if (u != null) {
                vo.setNickname(u.getNickname());
                vo.setAvatar(u.getAvatar());
            }
            vo.setDescription(c.getDescription());
            vo.setContact(c.getContact());
            vo.setStatus(c.getStatus());
            vo.setCreateTime(c.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
    }

    /** 站点队列专用转换：批量取标题，避免 detail() 的浏览量自增副作用 */
    private List<ClaimVO> toSimpleVO(List<ClaimRequest> list) {
        if (list.isEmpty()) return List.of();
        Set<Long> userIds = list.stream().map(ClaimRequest::getUserId).collect(Collectors.toSet());
        Map<Long, User> users = userIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        Set<Long> foundIds = list.stream().map(ClaimRequest::getItemId).collect(Collectors.toSet());
        Map<Long, String> titles = foundIds.isEmpty() ? Map.of() : foundMapper.selectBatchIds(foundIds).stream()
                .collect(Collectors.toMap(Found::getId, f -> f.getTitle() == null ? "" : f.getTitle(), (a, b) -> a));
        return list.stream().map(c -> {
            ClaimVO vo = new ClaimVO();
            vo.setId(c.getId());
            vo.setItemType(c.getItemType());
            vo.setItemId(c.getItemId());
            vo.setItemTitle(titles.getOrDefault(c.getItemId(), "物品已删除"));
            vo.setUserId(c.getUserId());
            User u = users.get(c.getUserId());
            if (u != null) {
                vo.setNickname(u.getNickname());
                vo.setAvatar(u.getAvatar());
            }
            vo.setDescription(c.getDescription());
            vo.setContact(c.getContact());
            vo.setStatus(c.getStatus());
            vo.setCreateTime(c.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
    }
}
