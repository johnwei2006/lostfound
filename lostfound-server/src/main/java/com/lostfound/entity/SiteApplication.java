package com.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 招领站建站申请：PENDING -> APPROVED / REJECTED。
 * 审核通过后才创建 foundsite 与 foundsite_manager，审核前不产生站点。
 */
@Data
@TableName("foundsite_application")
public class SiteApplication {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long applicantUserId;
    private String name;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String phone;
    private String openTime;
    private String description;
    private String reason;
    private String contact;

    private String status = STATUS_PENDING;
    private Long reviewerUserId;
    private LocalDateTime reviewTime;
    private String reviewRemark;
    private Long createdSiteId;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
