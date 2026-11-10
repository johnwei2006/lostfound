package com.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lostfound.auth.UserContext;
import com.lostfound.common.BusinessException;
import com.lostfound.common.PageResult;
import com.lostfound.entity.ClaimRequest;
import com.lostfound.entity.Found;
import com.lostfound.entity.FoundReturn;
import com.lostfound.entity.FoundSite;
import com.lostfound.entity.FoundSiteManager;
import com.lostfound.entity.FoundTransfer;
import com.lostfound.entity.Message;
import com.lostfound.entity.User;
import com.lostfound.mapper.ClaimRequestMapper;
import com.lostfound.mapper.FoundMapper;
import com.lostfound.mapper.FoundReturnMapper;
import com.lostfound.mapper.FoundSiteManagerMapper;
import com.lostfound.mapper.FoundSiteMapper;
import com.lostfound.mapper.FoundTransferMapper;
import com.lostfound.mapper.UserMapper;
import com.lostfound.vo.ReturnRecordVO;
import com.lostfound.vo.SiteStatsVO;
import com.lostfound.vo.SourceInfoVO;
import com.lostfound.vo.TransferVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 拾物转交招领站：申请 -> 站点管理员审批 -> 入库 -> 归还。
 * found_transfer 保留完整审批链，found.source_type / transfer_status 控制站点清单可见性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final FoundTransferMapper transferMapper;
    private final FoundReturnMapper returnMapper;
    private final ReturnService returnService;
    private final FoundMapper foundMapper;
    private final FoundSiteMapper foundSiteMapper;
    private final FoundSiteManagerMapper managerMapper;
    private final ClaimRequestMapper claimRequestMapper;
    private final UserMapper userMapper;
    private final MessageService messageService;
    private final SitePermissionService sitePermission;
    private final CacheEvictService cacheEvict;
    private final ObjectMapper objectMapper;

    public void requireSite(Long siteId) {
        if (siteId == null || foundSiteMapper.selectById(siteId) == null) {
            throw new BusinessException("招领站不存在");
        }
    }

    /** 发布拾物时选择目标站点，由 ItemService.publish 在同一事务内调用 */
    public void applyForPublish(Found f, Long siteId, Long userId, String remark) {
        requireSite(siteId);
        FoundTransfer t = createTransfer(f.getId(), siteId, userId, remark);
        notifyManagers(siteId, f.getId(), "收到新的转交申请",
                "有用户申请将拾物「" + f.getTitle() + "」转交到本站，请及时处理。");
        log.info("转交申请已创建 transferId={} foundId={} siteId={}", t.getId(), f.getId(), siteId);
    }

    /** 已发布的个人拾物申请转交 */
    @Transactional
    public Long apply(Long foundId, Long siteId, Long userId, String remark) {
        if (foundId == null) throw new BusinessException("请选择要转交的物品");
        if (siteId == null) throw new BusinessException("请选择目标招领站");
        Found f = requireFound(foundId);
        if (!Objects.equals(f.getUserId(), userId) && !UserContext.isAdmin()) {
            throw new BusinessException(403, "无权转交他人登记的物品");
        }
        if ("APPROVED".equals(f.getTransferStatus())) {
            throw new BusinessException("该物品已在招领站清单中，无需重复转交");
        }
        if (Found.STATUS_FINISHED.equals(f.getStatus())) {
            throw new BusinessException("已完成归还的物品不能转交");
        }
        requireSite(siteId);
        long pending = transferMapper.selectCount(new QueryWrapper<FoundTransfer>()
                .eq("found_id", foundId).eq("status", FoundTransfer.STATUS_PENDING));
        if (pending > 0) throw new BusinessException("该物品已有待审批的转交申请");

        if (f.getSubmitUserId() == null) f.setSubmitUserId(f.getUserId());
        f.setSiteId(siteId);
        f.setSourceType("PERSONAL");
        f.setTransferStatus(FoundTransfer.STATUS_PENDING);
        foundMapper.updateById(f);

        FoundTransfer t = createTransfer(foundId, siteId, userId, remark);
        notifyManagers(siteId, foundId, "收到新的转交申请",
                "有用户申请将拾物「" + f.getTitle() + "」转交到本站，请及时处理。");
        cacheEvict.evictLatest();
        log.info("转交申请已创建 transferId={} foundId={} siteId={}", t.getId(), foundId, siteId);
        return t.getId();
    }

    @Transactional
    public void cancel(Long transferId, Long userId) {
        FoundTransfer t = requireTransfer(transferId);
        if (!Objects.equals(t.getFromUserId(), userId) && !UserContext.isAdmin()) {
            throw new BusinessException(403, "无权撤回他人的转交申请");
        }
        cancelInternal(t, "申请人主动撤回");
    }

    /** 撤回某物品当前待审批的申请（/api/found/{id}/transfer DELETE） */
    @Transactional
    public void cancelLatest(Long foundId, Long userId) {
        FoundTransfer t = transferMapper.selectOne(new QueryWrapper<FoundTransfer>()
                .eq("found_id", foundId).eq("status", FoundTransfer.STATUS_PENDING)
                .orderByDesc("create_time").last("limit 1"));
        if (t == null) throw new BusinessException("没有待审批的转交申请");
        if (!Objects.equals(t.getFromUserId(), userId) && !UserContext.isAdmin()) {
            throw new BusinessException(403, "无权撤回他人的转交申请");
        }
        cancelInternal(t, "申请人主动撤回");
    }

    private void cancelInternal(FoundTransfer t, String reason) {
        if (!FoundTransfer.STATUS_PENDING.equals(t.getStatus())) {
            throw new BusinessException("仅待审批的申请可撤回");
        }
        LocalDateTime now = LocalDateTime.now();
        t.setStatus(FoundTransfer.STATUS_CANCELLED);
        t.setRemark(reason);
        t.setUpdateTime(now);
        transferMapper.updateById(t);

        Found f = foundMapper.selectById(t.getFoundId());
        if (f != null && FoundTransfer.STATUS_PENDING.equals(f.getTransferStatus())) {
            f.setTransferStatus(FoundTransfer.STATUS_CANCELLED);
            f.setSourceType("PERSONAL");
            foundMapper.updateById(f);
        }
        notifyManagers(t.getSiteId(), t.getFoundId(), "转交申请已撤回", "申请人已撤回转交申请，无需处理。");
        cacheEvict.evictLatest();
        log.warn("转交申请撤回 transferId={} foundId={} siteId={}", t.getId(), t.getFoundId(), t.getSiteId());
    }

    /** 站点管理员审批：通过后物品进入站点清单，驳回/意见回写申请人 */
    @Transactional
    public void review(Long transferId, Long reviewerId, boolean approve, String remark) {
        FoundTransfer t = requireTransfer(transferId);
        if (!FoundTransfer.STATUS_PENDING.equals(t.getStatus())) {
            throw new BusinessException("该申请已处理");
        }
        sitePermission.requireManager(t.getSiteId());
        LocalDateTime now = LocalDateTime.now();
        t.setStatus(approve ? FoundTransfer.STATUS_APPROVED : FoundTransfer.STATUS_REJECTED);
        t.setReviewerId(reviewerId);
        t.setReviewTime(now);
        t.setRemark(remark);
        t.setUpdateTime(now);
        transferMapper.updateById(t);

        Found f = foundMapper.selectById(t.getFoundId());
        if (f == null) throw new BusinessException("拾物信息不存在");
        if (approve) {
            f.setSiteId(t.getSiteId());
            f.setSourceType("TRANSFER");
            f.setTransferStatus(FoundTransfer.STATUS_APPROVED);
            f.setSiteApprovedBy(reviewerId);
            f.setSiteApprovedAt(now);
        } else {
            f.setSourceType("PERSONAL");
            f.setTransferStatus(FoundTransfer.STATUS_REJECTED);
        }
        foundMapper.updateById(f);

        messageService.send(t.getFromUserId(),
                approve ? "转交申请已通过" : "转交申请被驳回",
                approve ? "您转交的拾物已通过站点审批，现已在招领站清单展示，可在个人中心查看后续归还进度。"
                        : "您转交的拾物未通过站点审批" + (remark == null || remark.isBlank() ? "。" : "：" + remark),
                Message.TYPE_TRANSFER, t.getFoundId());
        cacheEvict.evictLatest();
        log.warn("转交审批 transferId={} foundId={} siteId={} reviewerId={} approve={}",
                transferId, t.getFoundId(), t.getSiteId(), reviewerId, approve);
    }

    public PageResult<TransferVO> mine(Long userId, long page, long size) {
        Page<FoundTransfer> p = transferMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<FoundTransfer>().eq("from_user_id", userId).orderByDesc("create_time"));
        return pageOf(p, toVO(p.getRecords()));
    }

    public PageResult<TransferVO> listForSite(Long siteId, String status, String keyword, long page, long size) {
        sitePermission.requireManager(siteId);
        QueryWrapper<FoundTransfer> qw = new QueryWrapper<FoundTransfer>().eq("site_id", siteId);
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            qw.eq("status", status.trim().toUpperCase());
        }
        String kw = keyword == null ? null : keyword.trim();
        if (kw != null && !kw.isEmpty()) {
            List<Long> ids = foundMapper.selectList(new QueryWrapper<Found>()
                            .select("id").like("title", kw))
                    .stream().map(Found::getId).toList();
            if (ids.isEmpty()) return emptyPage(page, size);
            qw.in("found_id", ids);
        }
        qw.orderByDesc("create_time");
        Page<FoundTransfer> p = transferMapper.selectPage(new Page<>(page, size), qw);
        return pageOf(p, toVO(p.getRecords()));
    }

    public TransferVO detail(Long transferId) {
        FoundTransfer t = requireTransfer(transferId);
        Long userId = UserContext.getUserId();
        if (!Objects.equals(t.getFromUserId(), userId) && !sitePermission.canManage(t.getSiteId())) {
            throw new BusinessException(403, "无权查看该转交申请");
        }
        return toVO(List.of(t)).get(0);
    }

    /** 源头信息：站点管理员或原始登记人可见 */
    public SourceInfoVO sourceInfo(Long foundId) {
        Found f = requireFound(foundId);
        Long userId = UserContext.getUserId();
        boolean allowed = sitePermission.canManage(f.getSiteId())
                || Objects.equals(f.getUserId(), userId)
                || Objects.equals(f.getSubmitUserId(), userId);
        if (!allowed) throw new BusinessException(403, "无权查看该物品源头信息");

        FoundTransfer t = transferMapper.selectOne(new QueryWrapper<FoundTransfer>()
                .eq("found_id", foundId).orderByDesc("create_time").last("limit 1"));
        Set<Long> userIds = new HashSet<>();
        userIds.add(f.getUserId());
        if (f.getSubmitUserId() != null) userIds.add(f.getSubmitUserId());
        if (t != null) {
            userIds.add(t.getFromUserId());
            if (t.getReviewerId() != null) userIds.add(t.getReviewerId());
        }
        Map<Long, User> users = userIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));

        SourceInfoVO vo = new SourceInfoVO();
        vo.setFoundId(f.getId());
        vo.setTitle(f.getTitle());
        vo.setCategory(f.getCategory());
        vo.setDescription(f.getDescription());
        vo.setAddress(f.getAddress());
        vo.setContact(f.getContact());
        vo.setImages(parseImages(f.getImages()));
        vo.setFoundTime(f.getFoundTime());
        vo.setCreateTime(f.getCreateTime());
        vo.setStatus(f.getStatus());
        vo.setSourceType(f.getSourceType());
        vo.setTransferStatus(f.getTransferStatus());
        vo.setOriginalUserId(f.getSubmitUserId() == null ? f.getUserId() : f.getSubmitUserId());
        User original = users.get(vo.getOriginalUserId());
        vo.setOriginalNickname(original == null ? "未知用户" : original.getNickname());
        vo.setOriginalPhone(original == null ? null : original.getPhone());
        vo.setSiteId(f.getSiteId());
        if (f.getSiteId() != null) {
            FoundSite site = foundSiteMapper.selectById(f.getSiteId());
            vo.setSiteName(site == null ? null : site.getName());
        }
        if (t != null) {
            vo.setTransferId(t.getId());
            vo.setFromUserId(t.getFromUserId());
            User from = users.get(t.getFromUserId());
            vo.setFromNickname(from == null ? "未知用户" : from.getNickname());
            vo.setSubmitTime(t.getCreateTime());
            vo.setTransferRemark(t.getRemark());
            vo.setReviewerId(t.getReviewerId());
            User reviewer = t.getReviewerId() == null ? null : users.get(t.getReviewerId());
            vo.setReviewerName(reviewer == null ? null : reviewer.getNickname());
            vo.setReviewTime(t.getReviewTime());
            vo.setReviewRemark(FoundTransfer.STATUS_PENDING.equals(t.getStatus()) ? null : t.getRemark());
        }
        vo.setReturns(returnService.byFound(foundId));
        return vo;
    }

    /** 站点管理后台概览统计 */
    public SiteStatsVO stats(Long siteId) {
        sitePermission.requireManager(siteId);
        SiteStatsVO vo = new SiteStatsVO();
        vo.setSiteId(siteId);
        vo.setPendingTransfers(transferMapper.selectCount(new QueryWrapper<FoundTransfer>()
                .eq("site_id", siteId).eq("status", FoundTransfer.STATUS_PENDING)));
        vo.setSiteItems(foundMapper.selectCount(new QueryWrapper<Found>()
                .eq("site_id", siteId)
                .and(w -> w.eq("source_type", "SITE").or().eq("transfer_status", FoundTransfer.STATUS_APPROVED))));
        vo.setReturnedItems(returnMapper.selectCount(new QueryWrapper<FoundReturn>().eq("site_id", siteId)));

        List<Long> itemIds = foundMapper.selectList(new QueryWrapper<Found>()
                        .select("id").eq("site_id", siteId))
                .stream().map(Found::getId).toList();
        LocalDateTime today = LocalDate.now().atStartOfDay();
        long handledTransfers = transferMapper.selectCount(new QueryWrapper<FoundTransfer>()
                .eq("site_id", siteId).ne("status", FoundTransfer.STATUS_PENDING).ge("review_time", today));
        long handledClaims = 0;
        if (!itemIds.isEmpty()) {
            vo.setPendingClaims(claimRequestMapper.selectCount(new QueryWrapper<ClaimRequest>()
                    .eq("item_type", "FOUND").eq("status", ClaimRequest.STATUS_PENDING).in("item_id", itemIds)));
            handledClaims = claimRequestMapper.selectCount(new QueryWrapper<ClaimRequest>()
                    .eq("item_type", "FOUND").ne("status", ClaimRequest.STATUS_PENDING)
                    .in("item_id", itemIds).ge("update_time", today));
        }
        vo.setTodayHandled(handledTransfers + handledClaims);
        return vo;
    }

    // ------------------------------------------------------------------
    // VO 组装
    // ------------------------------------------------------------------

    private List<TransferVO> toVO(List<FoundTransfer> list) {
        if (list.isEmpty()) return List.of();
        Set<Long> foundIds = list.stream().map(FoundTransfer::getFoundId).collect(Collectors.toSet());
        Map<Long, Found> founds = foundMapper.selectBatchIds(foundIds).stream()
                .collect(Collectors.toMap(Found::getId, Function.identity(), (a, b) -> a));
        Set<Long> siteIds = list.stream().map(FoundTransfer::getSiteId).collect(Collectors.toSet());
        Map<Long, String> sites = siteIds.isEmpty() ? Map.of() : foundSiteMapper.selectBatchIds(siteIds).stream()
                .collect(Collectors.toMap(FoundSite::getId, FoundSite::getName, (a, b) -> a));
        Set<Long> userIds = new HashSet<>();
        list.forEach(t -> {
            userIds.add(t.getFromUserId());
            if (t.getReviewerId() != null) userIds.add(t.getReviewerId());
        });
        Map<Long, String> users = userIds.isEmpty() ? Map.of() : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getNickname() == null ? "用户" + u.getId() : u.getNickname(), (a, b) -> a));
        Map<Long, ReturnRecordVO> latestReturns = new java.util.HashMap<>();
        if (!foundIds.isEmpty()) {
            for (ReturnRecordVO r : returnService.toVO(returnMapper.selectList(
                    new QueryWrapper<FoundReturn>().in("found_id", foundIds).orderByDesc("return_time")))) {
                latestReturns.putIfAbsent(r.getFoundId(), r);
            }
        }

        return list.stream().map(t -> {
            TransferVO vo = new TransferVO();
            vo.setId(t.getId());
            vo.setFoundId(t.getFoundId());
            Found f = founds.get(t.getFoundId());
            vo.setItemTitle(f == null ? "物品已删除" : f.getTitle());
            vo.setItemCategory(f == null ? null : f.getCategory());
            vo.setItemImages(f == null ? List.of() : parseImages(f.getImages()));
            vo.setItemStatus(f == null ? null : f.getStatus());
            vo.setSiteId(t.getSiteId());
            vo.setSiteName(sites.get(t.getSiteId()));
            vo.setFromUserId(t.getFromUserId());
            vo.setFromNickname(users.get(t.getFromUserId()));
            vo.setStatus(t.getStatus());
            vo.setRemark(t.getRemark());
            vo.setReviewerId(t.getReviewerId());
            vo.setReviewerName(t.getReviewerId() == null ? null : users.get(t.getReviewerId()));
            vo.setReviewTime(t.getReviewTime());
            vo.setCreateTime(t.getCreateTime());
            vo.setUpdateTime(t.getUpdateTime());
            ReturnRecordVO r = latestReturns.get(t.getFoundId());
            vo.setReturned(r != null);
            vo.setReturnTime(r == null ? null : r.getReturnTime());
            vo.setReturnWay(r == null ? null : r.getWay());
            vo.setTimeline(timeline(t, r));
            return vo;
        }).collect(Collectors.toList());
    }

    private List<TransferVO.TimelineNode> timeline(FoundTransfer t, ReturnRecordVO r) {
        List<TransferVO.TimelineNode> nodes = new ArrayList<>();
        nodes.add(node("submit", "提交转交申请", "目标站点：" + (t.getSiteId() == null ? "未知" : t.getSiteId()),
                t.getCreateTime(), "done"));
        switch (t.getStatus()) {
            case FoundTransfer.STATUS_PENDING -> nodes.add(node("review", "等待站点管理员审批", "审批通过后物品将进入站点清单",
                    null, "current"));
            case FoundTransfer.STATUS_APPROVED -> {
                nodes.add(node("review", "站点审批通过", t.getRemark(), t.getReviewTime(), "done"));
                nodes.add(node("instock", "物品已进入站点清单", "站点管理员可继续处理认领与归还", t.getReviewTime(), "done"));
                if (r != null) {
                    nodes.add(node("return", "物品已归还", "归还方式：" + ("CLAIM".equals(r.getWay()) ? "认领审批" : "现场登记"),
                            r.getReturnTime(), "done"));
                } else {
                    nodes.add(node("return", "等待归还", "认领审批通过或现场登记后完成归还", null, "current"));
                }
            }
            case FoundTransfer.STATUS_REJECTED -> nodes.add(node("review", "站点审批驳回", t.getRemark(),
                    t.getReviewTime(), "reject"));
            case FoundTransfer.STATUS_CANCELLED -> nodes.add(node("review", "已撤回申请", t.getRemark(),
                    t.getUpdateTime(), "reject"));
            default -> nodes.add(node("review", "申请状态：" + t.getStatus(), null, t.getUpdateTime(), "pending"));
        }
        return nodes;
    }

    private TransferVO.TimelineNode node(String key, String title, String detail, LocalDateTime time, String state) {
        TransferVO.TimelineNode n = new TransferVO.TimelineNode();
        n.setKey(key);
        n.setTitle(title);
        n.setDetail(detail);
        n.setTime(time);
        n.setState(state);
        return n;
    }

    private FoundTransfer createTransfer(Long foundId, Long siteId, Long userId, String remark) {
        FoundTransfer t = new FoundTransfer();
        t.setFoundId(foundId);
        t.setSiteId(siteId);
        t.setFromUserId(userId);
        t.setStatus(FoundTransfer.STATUS_PENDING);
        t.setRemark(remark);
        t.setCreateTime(LocalDateTime.now());
        t.setUpdateTime(LocalDateTime.now());
        transferMapper.insert(t);
        return t;
    }

    private void notifyManagers(Long siteId, Long relatedId, String title, String content) {
        Set<Long> ids = new LinkedHashSet<>(managerMapper.selectList(new QueryWrapper<FoundSiteManager>()
                        .select("user_id").eq("site_id", siteId))
                .stream().map(FoundSiteManager::getUserId).filter(Objects::nonNull).toList());
        for (Long uid : ids) {
            messageService.send(uid, title, content, Message.TYPE_TRANSFER, relatedId);
        }
    }

    private Found requireFound(Long id) {
        Found f = foundMapper.selectById(id);
        if (f == null) throw new BusinessException("拾物信息不存在");
        return f;
    }

    private FoundTransfer requireTransfer(Long id) {
        FoundTransfer t = transferMapper.selectById(id);
        if (t == null) throw new BusinessException("转交申请不存在");
        return t;
    }

    private PageResult<TransferVO> pageOf(Page<FoundTransfer> p, List<TransferVO> records) {
        PageResult<TransferVO> r = new PageResult<>();
        r.setTotal(p.getTotal());
        r.setPages(p.getPages());
        r.setCurrent(p.getCurrent());
        r.setSize(p.getSize());
        r.setRecords(records);
        return r;
    }

    private PageResult<TransferVO> emptyPage(long page, long size) {
        PageResult<TransferVO> r = new PageResult<>();
        r.setTotal(0);
        r.setPages(0);
        r.setCurrent(page);
        r.setSize(size);
        r.setRecords(List.of());
        return r;
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
