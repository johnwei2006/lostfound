package com.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 失物（丢失物品发布的寻物启事）
 */
@Data
@TableName("lost")
public class Lost {

    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_CLAIMED = "CLAIMED";
    public static final String STATUS_FINISHED = "FINISHED";
    public static final String STATUS_OFFLINE = "OFFLINE";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String title;
    private String category;
    private String description;
    private String images;
    private String province;
    private String city;
    private String district;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private LocalDateTime lostTime;
    private String contact;
    private String status;
    private String reviewStatus;
    private Integer viewCount;

    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
