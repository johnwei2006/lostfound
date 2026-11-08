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
    private Integer viewCount;

    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
