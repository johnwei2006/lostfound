package com.lostfound.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 站点管理员审批转交申请。
 */
@Data
public class TransferReviewDTO {

    @NotNull(message = "请给出审批结果")
    private Boolean approve;

    @Size(max = 500, message = "审批意见最长 500 字")
    private String remark;
}
