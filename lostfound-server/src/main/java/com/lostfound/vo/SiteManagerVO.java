package com.lostfound.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站点管理员列表与详情共用视图。不返回 openid、手机号等敏感字段，
 * contact 只提供脱敏后的展示值。
 */
@Data
public class SiteManagerVO {

    private Long userId;
    private String nickname;
    private String avatar;
    private Boolean isOwner;
    private LocalDateTime joinTime;
    private String contactMasked;
}
