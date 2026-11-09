package com.lostfound.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 归还记录展示对象。
 */
@Data
public class ReturnRecordVO {

    private Long id;
    private Long foundId;
    private String itemTitle;
    private Long siteId;
    private String siteName;
    private Long claimId;
    /** CLAIM-认领审批 / ONSITE-现场登记 */
    private String way;
    private String receiverName;
    private String receiverPhone;
    private List<String> evidence;
    private String remark;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime returnTime;
    private LocalDateTime createTime;
}
