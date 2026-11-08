package com.lostfound.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AdminStatsVO {

    private long userCount;
    private long foundCount;
    private long lostCount;
    private long waitingFound;
    private long waitingLost;
    private long pendingReview;
    private long matchCount;
    private long claimPending;

    /** 近 14 天发布趋势：date / found / lost */
    private List<Map<String, Object>> trend;

    /** 类别分布：category / count */
    private List<Map<String, Object>> categoryStats;
}
