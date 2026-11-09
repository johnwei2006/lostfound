package com.lostfound.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 物品视图对象（失物/拾物通用）
 */
@Data
public class ItemVO {

    private Long id;
    /** FOUND-拾物 / LOST-失物 */
    private String type;
    private Long userId;
    private String nickname;
    private String avatar;
    private Long siteId;
    private String siteName;

    private String title;
    private String category;
    private String description;
    private List<String> images;

    private String province;
    private String city;
    private String district;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;
    /** 距当前用户的公里数；未定位或物品无坐标时为 null */
    private Double distance;

    /** 拾物=拾获时间；失物=丢失时间 */
    private LocalDateTime itemTime;
    private String contact;
    private String status;
    private String reviewStatus;
    /** 站点来源：PERSONAL / SITE / TRANSFER */
    private String sourceType;
    /** 站点转交状态：NONE / PENDING / APPROVED / REJECTED / CANCELLED */
    private String transferStatus;
    /** 源头登记人 */
    private Long submitUserId;
    private Integer viewCount;

    private Long focusCount;
    private Long commentCount;
    /** 当前用户是否已关注 */
    private Boolean focused;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
