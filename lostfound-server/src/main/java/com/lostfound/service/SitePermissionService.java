package com.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lostfound.auth.UserContext;
import com.lostfound.common.BusinessException;
import com.lostfound.entity.FoundSite;
import com.lostfound.entity.FoundSiteManager;
import com.lostfound.mapper.FoundSiteManagerMapper;
import com.lostfound.mapper.FoundSiteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 招领站级权限：管理员关系只来源 foundsite_manager，不给 user.role 增加新角色。
 * 所有站点写操作在 Service 层显式调用 requireManager，避免依赖前端隐藏按钮。
 */
@Service
@RequiredArgsConstructor
public class SitePermissionService {

    private final FoundSiteManagerMapper foundSiteManagerMapper;
    private final FoundSiteMapper foundSiteMapper;

    /** 指定用户是否为指定站点管理员（不含全局 ADMIN 语义） */
    public boolean isManager(Long userId, Long siteId) {
        if (userId == null || siteId == null) return false;
        return foundSiteManagerMapper.selectCount(new QueryWrapper<FoundSiteManager>()
                .eq("site_id", siteId).eq("user_id", userId)) > 0;
    }

    /** 当前请求上下文是否可管理该站点：全局 ADMIN 直接放行 */
    public boolean canManage(Long siteId) {
        Long userId = UserContext.getUserId();
        if (userId == null) return false;
        return UserContext.isAdmin() || isManager(userId, siteId);
    }

    public void requireManager(Long siteId) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new BusinessException(401, "请先登录");
        if (siteId == null) throw new BusinessException(400, "缺少招领站ID");
        if (UserContext.isAdmin() || isManager(userId, siteId)) return;
        throw new BusinessException(403, "无权管理该招领站，请联系管理员");
    }

    /** 指定用户是否为该站主管理员（不含全局 ADMIN 语义） */
    public boolean isOwner(Long userId, Long siteId) {
        if (userId == null || siteId == null) return false;
        FoundSite site = foundSiteMapper.selectById(siteId);
        return site != null && Objects.equals(site.getOwnerUserId(), userId);
    }

    /**
     * 仅主管理员或全局 ADMIN 可执行：生成/撤销邀请码、移除共同管理员、转让主管理员。
     * 共同管理员仍可通过 requireManager 管理物品、审批与归还。
     */
    public void requireOwner(Long siteId) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new BusinessException(401, "请先登录");
        if (siteId == null) throw new BusinessException(400, "缺少招领站ID");
        if (UserContext.isAdmin()) return;
        if (!isOwner(userId, siteId)) {
            throw new BusinessException(403, "仅招领站主管理员可执行此操作");
        }
    }

    /** 我可管理的站点 ID；全局 ADMIN 返回全部站点 */
    public List<Long> mySiteIds(Long userId) {
        if (userId == null) return List.of();
        if (UserContext.isAdmin()) {
            return foundSiteMapper.selectList(new QueryWrapper<FoundSite>().select("id"))
                    .stream().map(FoundSite::getId).filter(Objects::nonNull).distinct().toList();
        }
        return foundSiteManagerMapper.selectList(new QueryWrapper<FoundSiteManager>()
                        .select("site_id").eq("user_id", userId))
                .stream().map(FoundSiteManager::getSiteId).filter(Objects::nonNull).distinct().toList();
    }

    public List<FoundSite> mySites(Long userId) {
        List<Long> ids = mySiteIds(userId);
        return ids.isEmpty() ? List.of() : foundSiteMapper.selectBatchIds(ids);
    }

    /** 是否具备站点管理入口（全局 ADMIN 或至少管理一个站点） */
    public boolean isSiteManager(Long userId) {
        if (UserContext.isAdmin()) return true;
        if (userId == null) return false;
        return foundSiteManagerMapper.selectCount(new QueryWrapper<FoundSiteManager>()
                .eq("user_id", userId)) > 0;
    }
}
