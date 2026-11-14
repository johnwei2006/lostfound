package com.lostfound.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SiteInviteVO {

    private Long id;
    private String code;
    private String status;
    private LocalDateTime expireTime;
    private LocalDateTime usedTime;
    private LocalDateTime createTime;
    /** 小程序码 data URL；微信接口不可用或新页面未发布时为 null，前端降级为纯邀请码 */
    private String qrCode;
}
