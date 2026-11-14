package com.lostfound.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.annotation.EnableCaching;

/**
 * MyBatis-Plus 配置：分页插件
 */
@Configuration
@EnableCaching
@MapperScan("com.lostfound.mapper")
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(100L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    /** 作为 MyBatis Interceptor bean 暴露，由 MyBatis-Plus 自动配置收集进 SqlSessionFactory 插件链 */
    @Bean
    public SlowSqlInterceptor slowSqlInterceptor(
            @Value("${lostfound.perf.slow-sql-ms:300}") long thresholdMs,
            @Value("${lostfound.perf.slow-sql-log-interval-seconds:10}") long intervalSeconds) {
        return new SlowSqlInterceptor(thresholdMs, intervalSeconds * 1000);
    }
}
