package com.lostfound.dto;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

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
    /** 按招领站过滤（拾物会自动排除未审批通过的转交物品） */
    private Long siteId;
    /** 来源筛选：PERSONAL / SITE / TRANSFER */
    private String sourceType;
    /** 转交状态筛选：NONE / PENDING / APPROVED / REJECTED / CANCELLED */
    private String transferStatus;

    private long page = 1;
    private long size = 10;

    /** 内部使用：当前登录用户（“只看我的”场景） */
    private Long currentUserId;

    /** 内部使用：/map 不需要分页总数；禁用 setter 防止被外部请求参数篡改 */
    @Setter(AccessLevel.NONE)
    private boolean countDisabled;
    /** 内部使用：/map 只返回带坐标的记录 */
    @Setter(AccessLevel.NONE)
    private boolean requireCoordinates;

    /**
     * 仅由服务端 /map 调用：在数据库侧下推坐标非空条件，并跳过无用的 COUNT。
     * 前端原本就会丢弃无坐标记录，因此展示结果保持不变。
     */
    public void enableMapMode() {
        this.countDisabled = true;
        this.requireCoordinates = true;
    }

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
