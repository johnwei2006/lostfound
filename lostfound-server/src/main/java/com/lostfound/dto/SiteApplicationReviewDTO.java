package com.lostfound.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SiteApplicationReviewDTO {

    @NotNull(message = "请给出审批结果")
    private Boolean approve;

    @Size(max = 500, message = "审核意见最长 500 字")
    private String remark;
}
