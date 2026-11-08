package com.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 失物-拾物智能匹配记录
 */
@Data
@TableName("match_record")
public class MatchRecord {

    public static final String STATUS_UNREAD = "UNREAD";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_IGNORED = "IGNORED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long foundId;
    private Long lostId;
    private BigDecimal score;
    private String reason;
    private String status;
    private LocalDateTime createTime;
}
