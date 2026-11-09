package com.lostfound.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/**
 * 统一管理首页 latestItems 缓存失效，避免新增站点写操作绕过 ItemService 后产生陈旧数据。
 */
@Service
@RequiredArgsConstructor
public class CacheEvictService {

    private final CacheManager cacheManager;

    public void evictLatest() {
        Cache cache = cacheManager.getCache("latestItems");
        if (cache != null) cache.clear();
    }
}
