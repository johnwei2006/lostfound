package com.lostfound.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 慢 SQL 可观测性：只记录 Mapper statement 全名、命令类型与耗时，
 * 不打印任何参数值（手机号 / openid / 邀请码 / 联系方式均不会出现在日志里）。
 * 按固定间隔采样限流，避免慢查询集中爆发时写爆日志。
 */
@Slf4j
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class,
                        ResultHandler.class, CacheKey.class, BoundSql.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class SlowSqlInterceptor implements Interceptor {

    private final long thresholdMs;
    private final long logIntervalMs;
    private final AtomicLong lastLogAt = new AtomicLong(0);

    public SlowSqlInterceptor(long thresholdMs, long logIntervalMs) {
        this.thresholdMs = Math.max(1L, thresholdMs);
        this.logIntervalMs = Math.max(0L, logIntervalMs);
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (cost >= thresholdMs && allowLog()) {
                MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
                log.warn("slow-sql statement={} command={} elapsedMs={}",
                        ms.getId(), ms.getSqlCommandType(), cost);
            }
        }
    }

    /** 简单 CAS 限流：一个采样窗口内最多输出一条慢 SQL 日志 */
    private boolean allowLog() {
        long now = System.currentTimeMillis();
        long last = lastLogAt.get();
        if (now - last < logIntervalMs) return false;
        return lastLogAt.compareAndSet(last, now);
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
}
