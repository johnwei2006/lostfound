package com.lostfound.dto;

import lombok.Data;

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
    /** 排序：new-最新 / hot-最热 */
    private String sort;
    /** 只看我的（需登录） */
    private Boolean mine;
    /** 按招领站过滤 */
    private Long siteId;

    private long page = 1;
    private long size = 10;

    /** 内部使用：当前登录用户（“只看我的”场景） */
    private Long currentUserId;
}
