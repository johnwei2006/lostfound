package com.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lostfound.common.BusinessException;
import com.lostfound.common.PageResult;
import com.lostfound.dto.SiteApplicationDTO;
import com.lostfound.dto.SiteApplicationReviewDTO;
import com.lostfound.dto.SiteDTO;
import com.lostfound.entity.FoundSite;
import com.lostfound.entity.FoundSiteManager;
import com.lostfound.entity.Message;
import com.lostfound.entity.SiteApplication;
import com.lostfound.entity.User;
import com.lostfound.mapper.FoundSiteManagerMapper;
import com.lostfound.mapper.FoundSiteMapper;
import com.lostfound.mapper.SiteApplicationMapper;
import com.lostfound.mapper.UserMapper;
import com.lostfound.vo.SiteApplicationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
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
 * 招领站建站申请：提交 -> ADMIN 审核 -> 通过后创建站点并绑定申请人为管理员。
 * 审核通过不调用全量覆盖式 setManagers，避免误删已有管理员或重复通知。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteApplicationService {

    private final SiteApplicationMapper applicationMapper;
    private final UserMapper userMapper;
    private final FoundSiteManagerMapper foundSiteManagerMapper;
    private final FoundSiteService foundSiteService;
    private final FoundSiteMapper foundSiteMapper;
    private final MessageService messageService;

    @Transactional
    public Long submit(Long userId, SiteApplicationDTO dto) {
        if (userId == null) throw new BusinessException(401, "请先登录");
        Long pending = applicationMapper.selectCount(new QueryWrapper<SiteApplication>()
                .eq("applicant_user_id", userId)
                .eq("status", SiteApplication.STATUS_PENDING));
        if (pending != null && pending > 0) throw new BusinessException("已提交过申请，请等待处理");

        SiteApplication app = new SiteApplication();
        BeanUtils.copyProperties(dto, app);
        app.setApplicantUserId(userId);
        app.setName(clean(dto.getName()));
        app.setAddress(clean(dto.getAddress()));
        app.setPhone(clean(dto.getPhone()));
        app.setOpenTime(clean(dto.getOpenTime()));
        app.setDescription(clean(dto.getDescription()));
        app.setReason(clean(dto.getReason()));
        app.setContact(clean(dto.getContact()));
        if (app.getName() == null) throw new BusinessException("站点名称不能为空");
        if (app.getAddress() == null) throw new BusinessException("站点地址不能为空");
        if (app.getReason() == null) throw new BusinessException("申请理由不能为空");
        app.setStatus(SiteApplication.STATUS_PENDING);
        LocalDateTime now = LocalDateTime.now();
        app.setCreateTime(now);
        app.setUpdateTime(now);
        applicationMapper.insert(app);
        log.warn("建站申请提交 applicationId={} applicantId={}", app.getId(), userId);
        return app.getId();
    }

    public List<SiteApplicationVO> mine(Long userId) {
        if (userId == null) throw new BusinessException(401, "请先登录");
        List<SiteApplication> list = applicationMapper.selectList(new QueryWrapper<SiteApplication>()
                .eq("applicant_user_id", userId).orderByDesc("create_time"));
        return toVOs(list);
    }

    public PageResult<SiteApplicationVO> page(String status, String keyword, long page, long size) {
        QueryWrapper<SiteApplication> qw = new QueryWrapper<>();
        String st = clean(status);
        if (st != null) qw.eq("status", st.toUpperCase());
        String kw = clean(keyword);
        if (kw != null) {
            qw.and(w -> w.like("name", kw).or().like("address", kw)
                    .or().like("reason", kw).or().like("contact", kw));
        }
        qw.orderByDesc("create_time");
        Page<SiteApplication> p = applicationMapper.selectPage(new Page<>(page, size), qw);
        PageResult<SiteApplicationVO> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPages(p.getPages());
        result.setCurrent(p.getCurrent());
        result.setSize(p.getSize());
        result.setRecords(toVOs(p.getRecords()));
        return result;
    }

    @Transactional
    public Long review(Long id, SiteApplicationReviewDTO dto, Long reviewerId) {
        if (dto.getApprove() == null) throw new BusinessException("请给出审批结果");
        SiteApplication app = applicationMapper.selectById(id);
        if (app == null) throw new BusinessException("建站申请不存在");
        if (!SiteApplication.STATUS_PENDING.equals(app.getStatus())) throw new BusinessException("该申请已处理");

        boolean approve = dto.getApprove();
        String remark = clean(dto.getRemark());
        if (!approve && remark == null) throw new BusinessException("请填写驳回意见");

        LocalDateTime now = LocalDateTime.now();
        int rows = applicationMapper.update(null, new UpdateWrapper<SiteApplication>()
                .eq("id", id)
                .eq("status", SiteApplication.STATUS_PENDING)
                .set("status", approve ? SiteApplication.STATUS_APPROVED : SiteApplication.STATUS_REJECTED)
                .set("reviewer_user_id", reviewerId)
                .set("review_time", now)
                .set("review_remark", remark)
                .set("update_time", now));
        if (rows == 0) throw new BusinessException("该申请已处理");

        if (!approve) {
            messageService.send(app.getApplicantUserId(), "建站申请未通过",
                    "您申请的「" + app.getName() + "」未通过审核：" + remark,
                    Message.TYPE_SITE, null);
            log.warn("建站申请驳回 applicationId={} reviewerId={}", id, reviewerId);
            return null;
        }

        User applicant = userMapper.selectById(app.getApplicantUserId());
        if (applicant == null || Integer.valueOf(0).equals(applicant.getStatus())
                || Integer.valueOf(1).equals(applicant.getDeleted())) {
            throw new BusinessException("申请人账号不可用，无法建站");
        }
        Long otherPending = applicationMapper.selectCount(new QueryWrapper<SiteApplication>()
                .eq("applicant_user_id", app.getApplicantUserId())
                .eq("status", SiteApplication.STATUS_PENDING)
                .ne("id", id));
        if (otherPending != null && otherPending > 0) {
            throw new BusinessException("申请人仍有其它待审申请，请先处理");
        }

        SiteDTO siteDTO = new SiteDTO();
        BeanUtils.copyProperties(app, siteDTO);
        Long siteId = foundSiteService.create(siteDTO);

        // 主管理员身份写 foundsite 单列，保证每站唯一；与管理员关系行在同一事务内提交
        FoundSite ownerSite = new FoundSite();
        ownerSite.setId(siteId);
        ownerSite.setOwnerUserId(app.getApplicantUserId());
        foundSiteMapper.updateById(ownerSite);

        FoundSiteManager manager = new FoundSiteManager();
        manager.setSiteId(siteId);
        manager.setUserId(app.getApplicantUserId());
        manager.setCreateTime(now);
        foundSiteManagerMapper.insert(manager);

        applicationMapper.update(null, new UpdateWrapper<SiteApplication>()
                .eq("id", id)
                .set("created_site_id", siteId));

        messageService.send(app.getApplicantUserId(), "建站申请已通过",
                "您申请的「" + app.getName() + "」招领站已创建，您已成为该站主管理员，可邀请共同管理员。",
                Message.TYPE_SITE, siteId);
        log.warn("建站申请通过 applicationId={} siteId={} applicantId={} reviewerId={}",
                id, siteId, app.getApplicantUserId(), reviewerId);
        return siteId;
    }

    @Transactional
    public void withdraw(Long id, Long userId) {
        if (userId == null) throw new BusinessException(401, "请先登录");
        int rows = applicationMapper.delete(new QueryWrapper<SiteApplication>()
                .eq("id", id).eq("applicant_user_id", userId)
                .eq("status", SiteApplication.STATUS_PENDING));
        if (rows == 0) throw new BusinessException("仅待审申请可撤回");
        log.warn("建站申请撤回 applicationId={} applicantId={}", id, userId);
    }

    private List<SiteApplicationVO> toVOs(List<SiteApplication> list) {
        if (list.isEmpty()) return List.of();
        Set<Long> ids = list.stream().map(SiteApplication::getApplicantUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> users = ids.isEmpty() ? Map.of() : userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return list.stream().map(app -> {
            SiteApplicationVO vo = new SiteApplicationVO();
            BeanUtils.copyProperties(app, vo);
            User u = users.get(app.getApplicantUserId());
            vo.setApplicantNickname(u == null || u.getNickname() == null
                    ? "用户#" + app.getApplicantUserId() : u.getNickname());
            vo.setStatusLabel(statusLabel(app.getStatus()));
            return vo;
        }).toList();
    }

    private static String statusLabel(String status) {
        if (SiteApplication.STATUS_PENDING.equals(status)) return "待审核";
        if (SiteApplication.STATUS_APPROVED.equals(status)) return "已通过";
        if (SiteApplication.STATUS_REJECTED.equals(status)) return "已驳回";
        return status;
    }

    private static String clean(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty() || "undefined".equalsIgnoreCase(v) || "null".equalsIgnoreCase(v)
                || "ALL".equalsIgnoreCase(v)) return null;
        return v;
    }
}
