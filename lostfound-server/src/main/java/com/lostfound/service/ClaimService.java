package com.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lostfound.auth.UserContext;
import com.lostfound.common.BusinessException;
import com.lostfound.dto.ClaimDTO;
import com.lostfound.entity.ClaimRequest;
import com.lostfound.entity.Found;
import com.lostfound.entity.Lost;
import com.lostfound.entity.User;
import com.lostfound.mapper.ClaimRequestMapper;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 认领流转服务：
 * 失主对拾物提交认领申请 → 发布者（或管理员）审核 → 通过后物品流转完成
 */
@Service
@RequiredArgsConstructor
public class ClaimService {

    private final ClaimRequestMapper claimRequestMapper;
    private final ItemService itemService;
    private final MessageService messageService;
    private final UserMapper userMapper;

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

    /** 审核申请：approve=true 则物品流转完成 */
    @Transactional
    public void review(Long claimId, Long reviewerId, boolean approve) {
        ClaimRequest c = claimRequestMapper.selectById(claimId);
        if (c == null) throw new BusinessException("申请不存在");
        if (!ClaimRequest.STATUS_PENDING.equals(c.getStatus())) {
            throw new BusinessException("该申请已处理");
        }
        Long ownerId = "FOUND".equals(c.getItemType())
                ? itemService.requireFound(c.getItemId()).getUserId()
                : itemService.requireLost(c.getItemId()).getUserId();
        if (!Objects.equals(ownerId, reviewerId) && !UserContext.isAdmin()) {
            throw new BusinessException(403, "仅发布者可审核认领申请");
        }

        c.setStatus(approve ? ClaimRequest.STATUS_APPROVED : ClaimRequest.STATUS_REJECTED);
        c.setUpdateTime(LocalDateTime.now());
        claimRequestMapper.updateById(c);

        if (approve) {
            itemService.changeStatus(c.getItemType(), c.getItemId(), reviewerId, true, "FINISHED");
        }

        String typeText = "FOUND".equals(c.getItemType()) ? "拾物" : "失物";
        messageService.send(c.getUserId(),
                approve ? "认领申请已通过" : "认领申请被拒绝",
                approve ? "恭喜！您的认领申请已通过审核，请联系发布者完成交接。"
                        : "很抱歉，您的认领申请未通过审核，可补充凭证后重新申请。",
                "CLAIM", c.getItemId());
        // 记录日志上下文
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
}
