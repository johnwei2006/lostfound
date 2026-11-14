package com.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 招领站管理员邀请码。主管理员生成，被邀请人在小程序内确认后一次性消费。
 * 邀请码落库而非放内存票据：需要可撤销、可审计，且后端重启后仍有效。
 */
@Data
@TableName("foundsite_manager_invite")
public class FoundSiteManagerInvite {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_USED = "USED";
    public static final String STATUS_REVOKED = "REVOKED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long siteId;
    private String code;
    private Long inviterUserId;
    private String status;
    private LocalDateTime expireTime;
    private Long usedByUserId;
    private LocalDateTime usedTime;
    private LocalDateTime createTime;
}
