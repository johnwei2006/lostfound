package com.lostfound.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 被邀请方确认页展示的只读信息，不回传邀请码本身 */
@Data
public class SiteInvitePreviewVO {

    private Long siteId;
    private String siteName;
    private String inviterNickname;
    private String status;
    private LocalDateTime expireTime;
    /** 当前用户是否已是该站管理员，用于提示「无需重复加入」 */
    private Boolean alreadyManager;
}
