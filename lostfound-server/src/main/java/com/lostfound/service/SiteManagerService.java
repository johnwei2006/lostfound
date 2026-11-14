package com.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lostfound.auth.UserContext;
import com.lostfound.common.BusinessException;
import com.lostfound.entity.FoundSite;
import com.lostfound.entity.FoundSiteManager;
import com.lostfound.entity.FoundSiteManagerInvite;
import com.lostfound.entity.Message;
import com.lostfound.entity.User;
import com.lostfound.mapper.FoundSiteManagerInviteMapper;
import com.lostfound.mapper.FoundSiteManagerMapper;
import com.lostfound.mapper.FoundSiteMapper;
import com.lostfound.mapper.UserMapper;
import com.lostfound.vo.SiteInvitePreviewVO;
import com.lostfound.vo.SiteInviteVO;
import com.lostfound.vo.SiteManagerVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 招领站主/共同管理员体系：管理员列表、详情、邀请码、移除与转让。
 *
 * 权限分层：列表/详情用 requireManager（共同管理员也可查看）；
 * 邀请码生成/撤销/移除/转让用 requireOwner（仅主管理员或全局 ADMIN）。
 * 前端隐藏按钮只是体验，所有写操作都在 Service 层校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteManagerService {

    /** 去掉 0/O/1/I 等易混淆字符，便于口头转达与手输 */
    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FoundSiteMapper foundSiteMapper;
    private final FoundSiteManagerMapper managerMapper;
    private final FoundSiteManagerInviteMapper inviteMapper;
    private final UserMapper userMapper;
    private final SitePermissionService sitePermission;
    private final MessageService messageService;
    private final WxAuthService wxAuthService;

    @Value("${lostfound.site-invite.ttl-hours:24}")
    private int defaultTtlHours;

    @Value("${lostfound.site-invite.page:pages/manage/invite-accept}")
    private String invitePage;

    @Value("${lostfound.site-invite.env-version:release}")
    private String inviteEnvVersion;

    /** 管理员列表：登录 + 本站管理员；主管理员置顶 */
    public List<SiteManagerVO> managers(Long siteId) {
        sitePermission.requireManager(siteId);
        return toVOs(requireSite(siteId));
    }

    /** 管理员详情复用列表结果，避免逐项请求；不返回 openid/手机号 */
    public SiteManagerVO detail(Long siteId, Long userId) {
        sitePermission.requireManager(siteId);
        return toVOs(requireSite(siteId)).stream()
                .filter(v -> Objects.equals(v.getUserId(), userId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("该用户不是本站管理员"));
    }

    @Transactional
    public SiteInviteVO createInvite(Long siteId, Integer ttlHours) {
        sitePermission.requireOwner(siteId);
        FoundSite site = requireSite(siteId);
        int ttl = (ttlHours == null || ttlHours < 1 || ttlHours > 168) ? defaultTtlHours : ttlHours;
        LocalDateTime now = LocalDateTime.now();

        String code = null;
        for (int i = 0; i < 6; i++) {
            String candidate = randomCode();
            if (inviteMapper.selectCount(new QueryWrapper<FoundSiteManagerInvite>()
                    .eq("code", candidate)) == 0) {
                code = candidate;
                break;
            }
        }
        if (code == null) throw new BusinessException("邀请码生成失败，请稍后重试");

        FoundSiteManagerInvite invite = new FoundSiteManagerInvite();
        invite.setSiteId(site.getId());
        invite.setCode(code);
        invite.setInviterUserId(UserContext.getUserId());
        invite.setStatus(FoundSiteManagerInvite.STATUS_PENDING);
        invite.setExpireTime(now.plusHours(ttl));
        invite.setCreateTime(now);
        inviteMapper.insert(invite);

        SiteInviteVO vo = toInviteVO(invite);
        vo.setQrCode(buildQrCode(code));
        log.warn("站点邀请码生成 siteId={} inviteId={} codePrefix={} operatorId={}",
                site.getId(), invite.getId(), preview(code), UserContext.getUserId());
        return vo;
    }

    /** 邀请码列表：仅主管理员；顺带把已过期的待用码标记为 EXPIRED */
    @Transactional
    public List<SiteInviteVO> invites(Long siteId) {
        sitePermission.requireOwner(siteId);
        inviteMapper.update(null, new UpdateWrapper<FoundSiteManagerInvite>()
                .eq("site_id", siteId)
                .eq("status", FoundSiteManagerInvite.STATUS_PENDING)
                .le("expire_time", LocalDateTime.now())
                .set("status", FoundSiteManagerInvite.STATUS_EXPIRED));
        return inviteMapper.selectList(new QueryWrapper<FoundSiteManagerInvite>()
                        .eq("site_id", siteId).orderByDesc("create_time"))
                .stream().map(this::toInviteVO).collect(Collectors.toList());
    }

    @Transactional
    public void revokeInvite(Long siteId, Long inviteId) {
        sitePermission.requireOwner(siteId);
        FoundSiteManagerInvite invite = inviteMapper.selectById(inviteId);
        if (invite == null || !Objects.equals(siteId, invite.getSiteId())) {
            throw new BusinessException("邀请码不存在");
        }
        if (!FoundSiteManagerInvite.STATUS_PENDING.equals(invite.getStatus())) {
            throw new BusinessException("仅可撤销待使用的邀请码");
        }
        inviteMapper.update(null, new UpdateWrapper<FoundSiteManagerInvite>()
                .eq("id", inviteId)
                .eq("status", FoundSiteManagerInvite.STATUS_PENDING)
                .set("status", FoundSiteManagerInvite.STATUS_REVOKED));
        log.warn("站点邀请码撤销 siteId={} inviteId={} operatorId={}",
                siteId, inviteId, UserContext.getUserId());
    }

    /** 被邀请方确认页的只读信息；不要求已是管理员 */
    public SiteInvitePreviewVO previewInvite(String rawCode) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new BusinessException(401, "请先登录");
        FoundSiteManagerInvite invite = findByCode(rawCode);
        FoundSite site = foundSiteMapper.selectById(invite.getSiteId());
        User inviter = userMapper.selectById(invite.getInviterUserId());

        SiteInvitePreviewVO vo = new SiteInvitePreviewVO();
        vo.setSiteId(invite.getSiteId());
        vo.setSiteName(site == null ? "招领站" : site.getName());
        vo.setInviterNickname(inviter == null ? "站点主管理员" : inviter.getNickname());
        vo.setStatus(effectiveStatus(invite));
        vo.setExpireTime(invite.getExpireTime());
        vo.setAlreadyManager(site != null && sitePermission.isManager(userId, invite.getSiteId()));
        return vo;
    }

    /**
     * 接受邀请：条件更新保证一次性消费与并发安全；
     * 已是管理员、站点已删除等情况抛异常，事务回滚会把邀请码恢复为 PENDING。
     */
    @Transactional
    public Long acceptInvite(String rawCode) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new BusinessException(401, "请先登录");
        String code = normalizeCode(rawCode);
        LocalDateTime now = LocalDateTime.now();

        int rows = inviteMapper.update(null, new UpdateWrapper<FoundSiteManagerInvite>()
                .eq("code", code)
                .eq("status", FoundSiteManagerInvite.STATUS_PENDING)
                .gt("expire_time", now)
                .set("status", FoundSiteManagerInvite.STATUS_USED)
                .set("used_by_user_id", userId)
                .set("used_time", now));
        if (rows == 0) {
            FoundSiteManagerInvite stale = findByCodeOrNull(code);
            if (stale == null) throw new BusinessException("邀请码不存在");
            if (!FoundSiteManagerInvite.STATUS_PENDING.equals(stale.getStatus())) {
                throw new BusinessException("邀请码已被使用或已撤销");
            }
            throw new BusinessException("邀请码已过期，请联系主管理员重新生成");
        }

        FoundSiteManagerInvite invite = findByCode(code);
        FoundSite site = foundSiteMapper.selectById(invite.getSiteId());
        if (site == null) throw new BusinessException("招领站不存在或已停用");
        if (sitePermission.isManager(userId, site.getId())) {
            throw new BusinessException("您已是该招领站的管理员");
        }

        FoundSiteManager manager = new FoundSiteManager();
        manager.setSiteId(site.getId());
        manager.setUserId(userId);
        manager.setCreateTime(now);
        managerMapper.insert(manager);

        User joined = userMapper.selectById(userId);
        String who = joined == null ? "新成员" : joined.getNickname();
        messageService.send(invite.getInviterUserId(), "新的共同管理员已加入",
                who + " 已通过邀请码加入「" + site.getName() + "」，成为共同管理员。",
                Message.TYPE_SITE, site.getId());
        messageService.send(userId, "您已成为招领站共同管理员",
                "您已加入「" + site.getName() + "」，可协助管理站内物品、转交审批与归还登记。",
                Message.TYPE_SITE, site.getId());
        log.warn("站点邀请码使用 siteId={} inviteId={} codePrefix={} userId={}",
                site.getId(), invite.getId(), preview(code), userId);
        return site.getId();
    }

    /** 移除共同管理员；主管理员不可被移除，需先转让 */
    @Transactional
    public void removeManager(Long siteId, Long targetUserId) {
        sitePermission.requireOwner(siteId);
        FoundSite site = requireSite(siteId);
        if (targetUserId == null) throw new BusinessException(400, "缺少管理员ID");
        if (Objects.equals(site.getOwnerUserId(), targetUserId)) {
            throw new BusinessException("不能移除主管理员，请先转让主管理员身份");
        }
        if (!sitePermission.isManager(targetUserId, siteId)) {
            throw new BusinessException("该用户不是本站管理员");
        }
        managerMapper.delete(new QueryWrapper<FoundSiteManager>()
                .eq("site_id", siteId).eq("user_id", targetUserId));
        messageService.send(targetUserId, "您已被移出招领站管理员",
                "您已不再是「" + site.getName() + "」的站点管理员。",
                Message.TYPE_SITE, siteId);
        log.warn("站点共同管理员移除 siteId={} targetUserId={} operatorId={}",
                siteId, targetUserId, UserContext.getUserId());
    }

    /**
     * 转让主管理员：目标必须是本站现有共同管理员。
     * 条件更新带旧 owner 值，避免并发下出现双主；原主保留管理员行（降为共同管理员）。
     */
    @Transactional
    public void transferOwner(Long siteId, Long targetUserId) {
        sitePermission.requireOwner(siteId);
        FoundSite site = requireSite(siteId);
        Long oldOwnerId = site.getOwnerUserId();
        if (targetUserId == null) throw new BusinessException(400, "请选择要转让的管理员");
        if (Objects.equals(oldOwnerId, targetUserId)) {
            throw new BusinessException("该用户已是主管理员");
        }
        if (!sitePermission.isManager(targetUserId, siteId)) {
            throw new BusinessException("只能转让给本站现有的共同管理员");
        }

        UpdateWrapper<FoundSite> uw = new UpdateWrapper<FoundSite>()
                .eq("id", siteId)
                .set("owner_user_id", targetUserId);
        if (oldOwnerId == null) uw.isNull("owner_user_id");
        else uw.eq("owner_user_id", oldOwnerId);
        if (foundSiteMapper.update(null, uw) == 0) {
            throw new BusinessException("主管理员已变更，请刷新后重试");
        }

        messageService.send(targetUserId, "您已成为招领站主管理员",
                "「" + site.getName() + "」的主管理员已转让给您，现在可以邀请共同管理员并再次转让。",
                Message.TYPE_SITE, siteId);
        if (oldOwnerId != null) {
            messageService.send(oldOwnerId, "主管理员身份已转让",
                    "您已将「" + site.getName() + "」的主管理员身份转让，当前身份变更为共同管理员。",
                    Message.TYPE_SITE, siteId);
        }
        log.warn("站点主管理员转让 siteId={} from={} to={} operatorId={}",
                siteId, oldOwnerId, targetUserId, UserContext.getUserId());
    }

    private List<SiteManagerVO> toVOs(FoundSite site) {
        List<FoundSiteManager> rows = managerMapper.selectList(
                new QueryWrapper<FoundSiteManager>().eq("site_id", site.getId()).orderByAsc("id"));
        if (rows.isEmpty()) return List.of();
        Set<Long> ids = rows.stream().map(FoundSiteManager::getUserId).collect(Collectors.toSet());
        Map<Long, User> users = userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        Long ownerId = site.getOwnerUserId();
        return rows.stream().map(m -> {
            User u = users.get(m.getUserId());
            SiteManagerVO vo = new SiteManagerVO();
            vo.setUserId(m.getUserId());
            vo.setNickname(u == null ? "未知用户" : u.getNickname());
            vo.setAvatar(u == null ? null : u.getAvatar());
            vo.setIsOwner(Objects.equals(ownerId, m.getUserId()));
            vo.setJoinTime(m.getCreateTime());
            vo.setContactMasked(mask(u == null ? null : u.getContact()));
            return vo;
        }).sorted(Comparator.comparing(SiteManagerVO::getIsOwner).reversed()
                        .thenComparing(SiteManagerVO::getJoinTime,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    private FoundSite requireSite(Long siteId) {
        if (siteId == null) throw new BusinessException(400, "缺少招领站ID");
        FoundSite site = foundSiteMapper.selectById(siteId);
        if (site == null) throw new BusinessException("招领站不存在");
        return site;
    }

    private FoundSiteManagerInvite findByCode(String rawCode) {
        FoundSiteManagerInvite invite = findByCodeOrNull(rawCode);
        if (invite == null) throw new BusinessException("邀请码不存在");
        return invite;
    }

    private FoundSiteManagerInvite findByCodeOrNull(String rawCode) {
        String code = normalizeCode(rawCode);
        if (code.isEmpty()) throw new BusinessException("请输入邀请码");
        return inviteMapper.selectOne(new QueryWrapper<FoundSiteManagerInvite>()
                .eq("code", code).last("LIMIT 1"));
    }

    private String effectiveStatus(FoundSiteManagerInvite invite) {
        if (FoundSiteManagerInvite.STATUS_PENDING.equals(invite.getStatus())
                && invite.getExpireTime() != null
                && !invite.getExpireTime().isAfter(LocalDateTime.now())) {
            return FoundSiteManagerInvite.STATUS_EXPIRED;
        }
        return invite.getStatus();
    }

    private String buildQrCode(String code) {
        try {
            return wxAuthService.getMiniProgramCode(code, invitePage, inviteEnvVersion);
        } catch (RuntimeException e) {
            log.warn("站点邀请码小程序码生成失败，降级为纯邀请码 codePrefix={} reason={}",
                    preview(code), e.getMessage());
            return null;
        }
    }

    private SiteInviteVO toInviteVO(FoundSiteManagerInvite invite) {
        SiteInviteVO vo = new SiteInviteVO();
        vo.setId(invite.getId());
        vo.setCode(invite.getCode());
        vo.setStatus(effectiveStatus(invite));
        vo.setExpireTime(invite.getExpireTime());
        vo.setUsedTime(invite.getUsedTime());
        vo.setCreateTime(invite.getCreateTime());
        return vo;
    }

    private static String normalizeCode(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) sb.append(CODE_CHARS[RANDOM.nextInt(CODE_CHARS.length)]);
        return sb.toString();
    }

    /** 日志只保留前 3 位，绝不打印完整邀请码 */
    private static String preview(String code) {
        return code == null || code.length() <= 3 ? "***" : code.substring(0, 3) + "***";
    }

    private static String mask(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        if (v.length() <= 4) return v.charAt(0) + "***";
        return v.substring(0, 2) + "****" + v.substring(v.length() - 2);
    }
}
