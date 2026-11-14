package com.lostfound.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/**
 * 统一管理业务缓存失效，避免写操作绕过 ItemService 后产生陈旧数据。
 *
 * <p>缓存一律带 TTL，这里只做「写后立即失效」以缩短不一致窗口：
 * 物品写入影响首页列表与分页总数；业务写操作影响统计概览。
 */
@Service
@RequiredArgsConstructor
public class CacheEvictService {

    /** 平台统计概览只有一个 key，与 AdminService 手写缓存共用 */
    public static final String OVERVIEW_CACHE_KEY = "overview";

    private final CacheManager cacheManager;

    /**
     * 物品/站点写入：首页最新列表 + 分页总数 + 全部统计概览失效。
     * 历史 10 处调用点保持同名兼容；统计缓存 TTL 只有 60 秒，统一清空比逐 key 追踪更不易遗漏。
     */
    public void evictLatest() {
        evictAll();
    }

    /** 平台统计概览失效（key 与 AdminService 手写缓存保持一致） */
    public void evictAdminOverview() {
        Cache cache = cacheManager.getCache("adminOverview");
        if (cache != null) cache.evict(OVERVIEW_CACHE_KEY);
    }

    /** 站点统计概览按站点失效；siteId 为空时清空整个缓存 */
    public void evictSiteStats(Long siteId) {
        Cache cache = cacheManager.getCache("siteStats");
        if (cache == null) return;
        if (siteId == null) cache.clear();
        else cache.evict(siteId);
    }

    /** 影响范围不确定的写操作统一兜底清理 */
    public void evictAll() {
        clear("latestItems");
        clear("itemCount");
        clear("siteStats");
        clear("adminOverview");
    }

    private void clear(String name) {
        Cache cache = cacheManager.getCache(name);
        if (cache != null) cache.clear();
    }
}
