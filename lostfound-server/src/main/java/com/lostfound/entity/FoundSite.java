package com.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("foundsite")
public class FoundSite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String phone;
    private String openTime;
    private String description;
    private String endorsementOrg;
    private String endorsementText;
    private LocalDateTime endorsementUpdatedAt;
    private Long endorsementUpdatedBy;
    /** 主管理员用户ID；必须同时存在于 foundsite_manager（迁移 verify 会校验） */
    private Long ownerUserId;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
