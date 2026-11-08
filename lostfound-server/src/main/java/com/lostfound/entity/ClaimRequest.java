package com.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 认领申请
 */
@Data
@TableName("claim_request")
public class ClaimRequest {

    public static final String TYPE_FOUND = "FOUND";
    public static final String TYPE_LOST = "LOST";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String itemType;
    private Long itemId;
    private Long userId;
    private String description;
    private String contact;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
