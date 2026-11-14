package com.lostfound.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * 分页总数短 TTL 缓存。
 *
 * <p>只缓存 COUNT 结果，不缓存记录本身，保证列表内容与排序始终来自数据库；
 * key 由规范化查询条件生成，不含手机号、openid、邀请码等敏感信息。
 * 任何物品写操作都会经 {@link CacheEvictService#evictLatest()} 整体清空。
 */
@Component
@RequiredArgsConstructor
public class ItemCountCache {

    private static final String NAME = "itemCount";

    private final CacheManager cacheManager;

    public Long get(String key) {
        Cache cache = cacheManager.getCache(NAME);
        if (cache == null) return null;
        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper == null || wrapper.get() == null) return null;
        return ((Number) wrapper.get()).longValue();
    }

    public void put(String key, long total) {
        Cache cache = cacheManager.getCache(NAME);
        if (cache != null) cache.put(key, total);
    }

    public void clear() {
        Cache cache = cacheManager.getCache(NAME);
        if (cache != null) cache.clear();
    }
}
