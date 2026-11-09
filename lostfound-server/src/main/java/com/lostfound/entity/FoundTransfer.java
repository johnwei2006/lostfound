package com.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 拾物转交招领站申请：NONE -> PENDING -> APPROVED / REJECTED / CANCELLED
 */
@Data
@TableName("found_transfer")
public class FoundTransfer {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long foundId;
    private Long siteId;
    private Long fromUserId;
    private String status;
    /** 转交备注或审批意见 */
    private String remark;
    private Long reviewerId;
    private LocalDateTime reviewTime;

    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
