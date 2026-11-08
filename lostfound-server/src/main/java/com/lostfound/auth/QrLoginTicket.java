package com.lostfound.auth;

import com.lostfound.entity.User;
import lombok.Data;

/**
 * Web 扫码登录票据：只存在于服务内存中，随 TTL 到期或服务重启自动失效。
 * 状态机：PENDING -> CONFIRMED ->（被 Web 轮询取走后从缓存删除）。
 */
@Data
public class QrLoginTicket {

    public static final String PENDING = "PENDING";
    public static final String CONFIRMED = "CONFIRMED";

    private String status = PENDING;
    /** 确认后为该用户签发的 JWT，只在一次性轮询里返回给 Web */
    private String token;
    private User user;
    private final long createdAt = System.currentTimeMillis();
}
