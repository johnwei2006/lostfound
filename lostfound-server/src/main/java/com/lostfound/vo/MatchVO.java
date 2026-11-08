package com.lostfound.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MatchVO {

    private Long id;
    private BigDecimal score;
    private String reason;
    private String status;
    private LocalDateTime createTime;

    /** 匹配到的拾物 */
    private ItemVO found;
    /** 匹配到的失物 */
    private ItemVO lost;
}
