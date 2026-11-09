package com.lostfound.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 转交申请视图：申请人可见状态时间线与后续归还进度，站点管理员可见申请人信息。
 */
@Data
public class TransferVO {

    private Long id;
    private Long foundId;
    private String itemTitle;
    private String itemCategory;
    private List<String> itemImages;
    private String itemStatus;

    private Long siteId;
    private String siteName;
    private Long fromUserId;
    private String fromNickname;

    /** PENDING / APPROVED / REJECTED / CANCELLED */
    private String status;
    private String remark;
    private Long reviewerId;
    private String reviewerName;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 审批通过后的归还摘要 */
    private Boolean returned;
    private LocalDateTime returnTime;
    private String returnWay;

    private List<TimelineNode> timeline;

    @Data
    public static class TimelineNode {
        private String key;
        private String title;
        private String detail;
        private LocalDateTime time;
        /** done / current / reject / pending */
        private String state;
    }
}
