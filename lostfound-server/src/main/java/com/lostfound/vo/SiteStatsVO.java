package com.lostfound.vo;

import lombok.Data;

/**
 * 站点管理后台概览统计。
 */
@Data
public class SiteStatsVO {

    private Long siteId;
    /** 待审批转交申请 */
    private long pendingTransfers;
    /** 待审批认领申请 */
    private long pendingClaims;
    /** 在站物品（已审批入库 / 站点直接发布，含已下架） */
    private long siteItems;
    /** 归还登记数 */
    private long returnedItems;
    /** 今日已处理的转交与认领申请 */
    private long todayHandled;
}
