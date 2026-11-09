package com.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 拾物归还登记：认领审批通过（CLAIM）与现场登记（ONSITE）两条路径统一落表。
 */
@Data
@TableName("found_return")
public class FoundReturn {

    public static final String WAY_CLAIM = "CLAIM";
    public static final String WAY_ONSITE = "ONSITE";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long foundId;
    private Long siteId;
    private Long claimId;
    private String way;
    private String receiverName;
    private String receiverPhone;
    /** 归还凭证图片 URL JSON 数组 */
    private String evidence;
    private String remark;
    private Long operatorId;
    private LocalDateTime returnTime;

    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
}
