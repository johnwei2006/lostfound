package com.lostfound.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lostfound.auth.QrLoginTicket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Caffeine 本地缓存配置：按业务用途分级 TTL，避免统计类缓存长时间陈旧。
 * 仅单机内存，进程重启即清空；所有缓存都必须带过期时间。
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(
            @Value("${lostfound.perf.latest-cache-seconds:300}") long latestSeconds,
            @Value("${lostfound.perf.stats-cache-seconds:60}") long statsSeconds,
            @Value("${lostfound.perf.count-cache-seconds:30}") long countSeconds) {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                caffeineCache("latestItems", latestSeconds, 500),
                // 统计概览与站点概览采用短 TTL，配合写操作显式失效保证准实时
                caffeineCache("siteStats", statsSeconds, 500),
                caffeineCache("adminOverview", statsSeconds, 100),
                // 分页总数按规范化查询条件缓存，写操作后整体清空
                caffeineCache("itemCount", countSeconds, 2000)));
        manager.afterPropertiesSet();
        return manager;
    }

    private CaffeineCache caffeineCache(String name, long ttlSeconds, long maxSize) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(Math.max(1L, ttlSeconds)))
                .maximumSize(Math.max(1L, maxSize))
                .build());
    }

    /** 票据单独配置 TTL 与容量，避免与业务缓存共用规格；仅单机内存，重启即清空 */
    @Bean
    public Cache<String, QrLoginTicket> loginTicketCache(
            @Value("${lostfound.qr-login.ticket-ttl-seconds:300}") long ttlSeconds,
            @Value("${lostfound.qr-login.max-tickets:2000}") long maxTickets) {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(maxTickets)
                .build();
    }
}
