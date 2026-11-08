package com.lostfound.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.lostfound.auth.JwtUtil;
import com.lostfound.auth.QrLoginTicket;
import com.lostfound.common.BusinessException;
import com.lostfound.entity.User;
import com.lostfound.mapper.UserMapper;
import com.lostfound.vo.QrStatusVO;
import com.lostfound.vo.QrTicketVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Web 扫码登录票据服务（单机内存态）。
 *
 * - 票据用 SecureRandom 生成 32 字符十六进制串，作为小程序码的 scene
 * - 小程序确认接口只绑定当前登录用户（即小程序 openid 对应的账号），Web 拿到同一个 user.id
 * - confirmed 状态被 Web 轮询取走后立刻删除，token 不可重放
 * - 票据不落库，服务重启或 TTL 到期自动失效；多实例部署时需替换为 Redis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QrLoginService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Cache<String, QrLoginTicket> loginTicketCache;
    private final WxAuthService wxAuthService;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final LogService logService;

    @Value("${lostfound.qr-login.ticket-ttl-seconds:300}")
    private long ticketTtlSeconds;

    @Value("${lostfound.qr-login.rate-limit-per-minute:10}")
    private int rateLimitPerMinute;

    /** 按 IP 的创建频控，避免刷微信二维码接口 */
    private final Cache<String, AtomicInteger> ticketRateLimiter = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(10_000)
            .build();

    public QrTicketVO createTicket(String ip) {
        checkRateLimit(ip);
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        String ticket = HexFormat.of().formatHex(bytes);

        QrLoginTicket state = new QrLoginTicket();
        loginTicketCache.put(ticket, state);
        String qrCode;
        try {
            qrCode = wxAuthService.getMiniProgramCode(ticket);
        } catch (RuntimeException e) {
            loginTicketCache.invalidate(ticket);
            throw e;
        }
        log.info("创建扫码登录票据 ticket={}… ip={}", preview(ticket), ip);
        return new QrTicketVO(ticket, qrCode, ticketTtlSeconds);
    }

    /** 轮询状态；confirmed 一次性消费，取走即从缓存删除 */
    public synchronized QrStatusVO status(String ticket) {
        if (ticket == null || ticket.isBlank()) return QrStatusVO.expired();
        QrLoginTicket state = loginTicketCache.getIfPresent(ticket);
        if (state == null) return QrStatusVO.expired();
        if (!QrLoginTicket.CONFIRMED.equals(state.getStatus())) return QrStatusVO.pending();

        loginTicketCache.invalidate(ticket);
        log.info("扫码登录票据已消费 ticket={}…", preview(ticket));
        return QrStatusVO.confirmed(state.getToken(), state.getUser());
    }

    /** 小程序确认：只使用 UserContext 传入的当前用户，不接受外部 userId */
    public synchronized void confirm(String ticket, Long userId, String ip) {
        QrLoginTicket state = loginTicketCache.getIfPresent(ticket);
        if (state == null || !QrLoginTicket.PENDING.equals(state.getStatus())) {
            throw new BusinessException("二维码已过期或已使用，请回到网页刷新二维码");
        }
        User user = userId == null ? null : userMapper.selectById(userId);
        if (user == null || Integer.valueOf(0).equals(user.getStatus())) {
            throw new BusinessException("当前账号不可用，无法完成登录");
        }

        state.setStatus(QrLoginTicket.CONFIRMED);
        state.setToken(jwtUtil.createToken(user.getId(), user.getRole()));
        state.setUser(user);
        logService.record(user.getId(), "WEB_LOGIN", "qr", ip);
        log.info("扫码登录已确认 ticket={}… userId={}", preview(ticket), user.getId());
    }

    private void checkRateLimit(String ip) {
        String key = ip == null || ip.isBlank() ? "unknown" : ip;
        AtomicInteger count = ticketRateLimiter.get(key, k -> new AtomicInteger(0));
        if (count.incrementAndGet() > Math.max(1, rateLimitPerMinute)) {
            throw new BusinessException(429, "操作过于频繁，请稍后再试");
        }
    }

    /** 日志只记录票据前缀，避免完整票据与 token 进入日志 */
    private String preview(String ticket) {
        return ticket == null || ticket.length() < 6 ? "***" : ticket.substring(0, 6);
    }
}
