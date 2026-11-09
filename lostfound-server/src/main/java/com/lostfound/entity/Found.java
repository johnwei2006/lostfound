package com.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 拾物（捡到物品发布的招领信息）
 */
@Data
@TableName("found")
public class Found {

    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_CLAIMED = "CLAIMED";
    public static final String STATUS_FINISHED = "FINISHED";
    public static final String STATUS_OFFLINE = "OFFLINE";

    /** 站点来源：PERSONAL-个人登记 / SITE-站点直接发布 / TRANSFER-转交审批入库 */
    public static final String SOURCE_PERSONAL = "PERSONAL";
    public static final String SOURCE_SITE = "SITE";
    public static final String SOURCE_TRANSFER = "TRANSFER";
    /** 站点转交状态，与 found_transfer.status 保持一致 */
    public static final String TRANSFER_NONE = "NONE";
    public static final String TRANSFER_PENDING = "PENDING";
    public static final String TRANSFER_APPROVED = "APPROVED";
    public static final String TRANSFER_REJECTED = "REJECTED";
    public static final String TRANSFER_CANCELLED = "CANCELLED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long siteId;
    private String title;
    private String category;
    private String description;
    /** 图片 URL JSON 数组 */
    private String images;
    private String province;
    private String city;
    private String district;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private LocalDateTime foundTime;
    private String contact;
    private String status;
    private String reviewStatus;
    /** 来源：PERSONAL / SITE / TRANSFER */
    private String sourceType;
    /** 站点转交状态：NONE / PENDING / APPROVED / REJECTED / CANCELLED */
    private String transferStatus;
    /** 源头登记人（转交后 userId 仍保留原登记人，此字段用于追溯） */
    private Long submitUserId;
    private Long siteApprovedBy;
    private LocalDateTime siteApprovedAt;
    private Integer viewCount;

    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
