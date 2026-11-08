package com.lostfound.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 创建扫码登录票据的返回体 */
@Data
@AllArgsConstructor
public class QrTicketVO {

    private String ticket;
    /** 小程序码：按实际格式生成的 data:image/(jpeg|png);base64,...，Web 无需额外二维码库 */
    private String qrCode;
    /** 票据有效秒数 */
    private long expiresIn;
}
