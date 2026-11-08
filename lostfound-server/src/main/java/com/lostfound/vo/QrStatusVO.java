package com.lostfound.vo;

import com.lostfound.entity.User;
import lombok.Data;

/** Web 轮询扫码登录状态的结果 */
@Data
public class QrStatusVO {

    public static final String PENDING = "pending";
    public static final String CONFIRMED = "confirmed";
    public static final String EXPIRED = "expired";

    private String status;
    private String token;
    private User user;

    public static QrStatusVO pending() {
        QrStatusVO vo = new QrStatusVO();
        vo.setStatus(PENDING);
        return vo;
    }

    public static QrStatusVO expired() {
        QrStatusVO vo = new QrStatusVO();
        vo.setStatus(EXPIRED);
        return vo;
    }

    public static QrStatusVO confirmed(String token, User user) {
        QrStatusVO vo = new QrStatusVO();
        vo.setStatus(CONFIRMED);
        vo.setToken(token);
        vo.setUser(user);
        return vo;
    }
}
