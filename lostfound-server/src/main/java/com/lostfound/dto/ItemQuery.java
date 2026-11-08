package com.lostfound.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 物品列表查询条件
 */
@Data
public class ItemQuery {

    /** 关键词（标题/描述模糊匹配） */
    private String keyword;
    /** 物品类别 */
    private String category;
    /** 城市 */
    private String city;
    /** 状态：WAITING / CLAIMED / FINISHED / OFFLINE，空为全部 */
    private String status;
    /** 最近 N 天 */
    private Integer days;
    /** 排序：distance-距离近到远（需合法 userLat/userLng）/ time、new-发布时间近到远 / hot-最热 */
    private String sort;
    /** 用户当前纬度（gcj02），仅 distance 排序使用 */
    private BigDecimal userLat;
    /** 用户当前经度（gcj02），仅 distance 排序使用 */
    private BigDecimal userLng;
    /** 只看我的（需登录） */
    private Boolean mine;
    /** 按招领站过滤 */
    private Long siteId;

    private long page = 1;
    private long size = 10;

    /** 内部使用：当前登录用户（“只看我的”场景） */
    private Long currentUserId;

    /**
     * 经纬度是否在合法范围内。SQL 中的距离表达式只允许拼接这些已校验的数值字面量，
     * 防止通过 sort=distance 注入原始 SQL。
     */
    public boolean hasValidCoordinates() {
        return userLat != null && userLng != null
                && userLat.abs().compareTo(BigDecimal.valueOf(90)) <= 0
                && userLng.abs().compareTo(BigDecimal.valueOf(180)) <= 0;
    }
}
