package com.lostfound.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 物品源头信息：原始登记、转交链路、审批信息与后续归还。
 * 仅站点管理员 / 全局 ADMIN / 原始登记人可见。
 */
@Data
public class SourceInfoVO {

    private Long foundId;
    private String title;
    private String category;
    private String description;
    private String address;
    private String contact;
    private List<String> images;
    private LocalDateTime foundTime;
    private LocalDateTime createTime;
    private String status;
    private String sourceType;
    private String transferStatus;

    private Long originalUserId;
    private String originalNickname;
    private String originalPhone;

    private Long siteId;
    private String siteName;
    private Long transferId;
    private Long fromUserId;
    private String fromNickname;
    private LocalDateTime submitTime;
    private String transferRemark;

    private Long reviewerId;
    private String reviewerName;
    private LocalDateTime reviewTime;
    private String reviewRemark;

    private List<ReturnRecordVO> returns;
}
