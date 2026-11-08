package com.lostfound.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 创建扫码登录票据的返回体 */
@Data
@AllArgsConstructor
public class QrTicketVO {

    private String ticket;
    /** 小程序码：data:image/png;base64,... 形式，Web 无需额外二维码库 */
    private String qrCode;
    /** 票据有效秒数 */
    private long expiresIn;
}
