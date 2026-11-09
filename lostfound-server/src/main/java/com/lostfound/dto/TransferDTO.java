package com.lostfound.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 将已有拾物申请转交到招领站。
 */
@Data
public class TransferDTO {

    @NotNull(message = "请选择要转交的物品")
    private Long foundId;

    @NotNull(message = "请选择目标招领站")
    private Long siteId;

    @Size(max = 500, message = "备注最长 500 字")
    private String remark;
}
