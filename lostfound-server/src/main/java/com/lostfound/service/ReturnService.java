package com.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lostfound.common.BusinessException;
import com.lostfound.common.PageResult;
import com.lostfound.dto.ReturnDTO;
import com.lostfound.entity.ClaimRequest;
import com.lostfound.entity.Found;
import com.lostfound.entity.FoundReturn;
import com.lostfound.entity.FoundSite;
import com.lostfound.entity.Message;
import com.lostfound.entity.User;
import com.lostfound.mapper.FoundMapper;
import com.lostfound.mapper.FoundReturnMapper;
import com.lostfound.mapper.FoundSiteMapper;
import com.lostfound.mapper.UserMapper;
import com.lostfound.vo.ReturnRecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * 归还管理：认领审批通过（CLAIM）与站点现场登记（ONSITE）两条路径统一写 found_return。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnService {

    private final FoundReturnMapper returnMapper;
    private final FoundMapper foundMapper;
    private final FoundSiteMapper foundSiteMapper;
    private final UserMapper userMapper;
    private final MessageService messageService;
    private final SitePermissionService sitePermission;
    private final CacheEvictService cacheEvict;
    private final ObjectMapper objectMapper;

    /** 站点管理员独立登记现场归还 */
    @Transactional
    public Long register(Long siteId, ReturnDTO dto, Long operatorId) {
        sitePermission.requireManager(siteId);
        Found f = foundMapper.selectById(dto.getFoundId());
        if (f == null) throw new BusinessException("拾物信息不存在");
        if (!Objects.equals(f.getSiteId(), siteId)) {
            throw new BusinessException(403, "该物品不属于当前招领站");
        }
        boolean inSite = "SITE".equals(f.getSourceType()) || "APPROVED".equals(f.getTransferStatus());
        if (!inSite) throw new BusinessException("该物品尚未通过站点审批，不能登记归还");
        if (returnMapper.selectCount(new QueryWrapper<FoundReturn>().eq("found_id", f.getId())) > 0) {
            throw new BusinessException("该物品已有归还记录");
        }

        FoundReturn r = new FoundReturn();
        r.setFoundId(f.getId());
        r.setSiteId(siteId);
        r.setWay(FoundReturn.WAY_ONSITE);
        r.setReceiverName(dto.getReceiverName());
        r.setReceiverPhone(dto.getReceiverPhone());
        r.setEvidence(toJson(dto.getEvidence()));
        r.setRemark(dto.getRemark());
        r.setOperatorId(operatorId);
        r.setReturnTime(LocalDateTime.now());
        returnMapper.insert(r);

        f.setStatus(Found.STATUS_FINISHED);
        foundMapper.updateById(f);
        cacheEvict.evictLatest();
        notifyReturn(f, "物品已完成归还登记", "物品已由招领站登记归还，可在个人中心查看后续进度。");
        log.warn("现场归还登记 returnId={} foundId={} siteId={} operatorId={}", r.getId(), f.getId(), siteId, operatorId);
        return r.getId();
    }

    /**
     * 认领审批通过后补写归还记录（由 ClaimService.review 在其事务内调用）。
     * 幂等：同一认领申请不重复落表。
     */
    public void recordClaimReturn(ClaimRequest c, Long operatorId) {
        if (c == null || !"FOUND".equals(c.getItemType())) return;
        if (returnMapper.selectCount(new QueryWrapper<FoundReturn>().eq("claim_id", c.getId())) > 0) return;
        Found f = foundMapper.selectById(c.getItemId());
        if (f == null) return;

        FoundReturn r = new FoundReturn();
        r.setFoundId(f.getId());
        r.setSiteId(f.getSiteId());
        r.setClaimId(c.getId());
        r.setWay(FoundReturn.WAY_CLAIM);
        User applicant = userMapper.selectById(c.getUserId());
        r.setReceiverName(applicant == null ? null : applicant.getNickname());
        r.setReceiverPhone(c.getContact() != null ? c.getContact() : (applicant == null ? null : applicant.getPhone()));
        r.setRemark("认领申请审核通过，自动登记归还");
        r.setOperatorId(operatorId);
        r.setReturnTime(LocalDateTime.now());
        returnMapper.insert(r);
        notifyReturn(f, "物品已确认归还", "认领申请审核通过，物品已标记为已完成。");
        log.warn("认领归还登记 returnId={} foundId={} claimId={} operatorId={}", r.getId(), f.getId(), c.getId(), operatorId);
    }

    public PageResult<ReturnRecordVO> listForSite(Long siteId, long page, long size) {
        sitePermission.requireManager(siteId);
        Page<FoundReturn> p = returnMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<FoundReturn>().eq("site_id", siteId).orderByDesc("return_time"));
        PageResult<ReturnRecordVO> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPages(p.getPages());
        result.setCurrent(p.getCurrent());
        result.setSize(p.getSize());
        result.setRecords(toVO(p.getRecords()));
        return result;
    }

    public List<ReturnRecordVO> byFound(Long foundId) {
        return toVO(returnMapper.selectList(new QueryWrapper<FoundReturn>()
                .eq("found_id", foundId).orderByDesc("return_time")));
    }

    /** 批量转换：物品名、站点名、操作人均用批量查询回填，避免 N+1 */
    public List<ReturnRecordVO> toVO(List<FoundReturn> rows) {
        if (rows.isEmpty()) return List.of();
        Set<Long> foundIds = rows.stream().map(FoundReturn::getFoundId).collect(Collectors.toSet());
        Map<Long, Found> founds = foundMapper.selectBatchIds(foundIds).stream()
                .collect(Collectors.toMap(Found::getId, Function.identity(), (a, b) -> a));
        Set<Long> siteIds = rows.stream().map(FoundReturn::getSiteId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> sites = siteIds.isEmpty() ? Map.of() : foundSiteMapper.selectBatchIds(siteIds).stream()
                .collect(Collectors.toMap(FoundSite::getId, FoundSite::getName, (a, b) -> a));
        Set<Long> userIds = rows.stream().map(FoundReturn::getOperatorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> users = userIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getNickname() == null ? "用户" + u.getId() : u.getNickname(), (a, b) -> a));

        return rows.stream().map(r -> {
            ReturnRecordVO vo = new ReturnRecordVO();
            vo.setId(r.getId());
            vo.setFoundId(r.getFoundId());
            Found f = founds.get(r.getFoundId());
            vo.setItemTitle(f == null ? "物品已删除" : f.getTitle());
            vo.setSiteId(r.getSiteId());
            vo.setSiteName(r.getSiteId() == null ? null : sites.get(r.getSiteId()));
            vo.setClaimId(r.getClaimId());
            vo.setWay(r.getWay());
            vo.setReceiverName(r.getReceiverName());
            vo.setReceiverPhone(r.getReceiverPhone());
            vo.setEvidence(parseImages(r.getEvidence()));
            vo.setRemark(r.getRemark());
            vo.setOperatorId(r.getOperatorId());
            vo.setOperatorName(users.get(r.getOperatorId()));
            vo.setReturnTime(r.getReturnTime());
            vo.setCreateTime(r.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
    }

    private void notifyReturn(Found f, String title, String content) {
        java.util.LinkedHashSet<Long> receivers = new java.util.LinkedHashSet<>();
        if (f.getUserId() != null) receivers.add(f.getUserId());
        if (f.getSubmitUserId() != null) receivers.add(f.getSubmitUserId());
        for (Long uid : receivers) {
            messageService.send(uid, title, content, Message.TYPE_RETURN, f.getId());
        }
    }

    private String toJson(List<String> images) {
        if (images == null || images.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> parseImages(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
