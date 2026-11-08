package com.lostfound.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lostfound.auth.QrLoginTicket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Caffeine 本地缓存配置：缓存热点列表数据与 Web 扫码登录票据
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("latestItems", "siteStats", "adminOverview");
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(Duration.ofMinutes(5)));
        return manager;
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
