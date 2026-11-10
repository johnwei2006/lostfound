package com.lostfound.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 将已有拾物申请转交到招领站。
 *
 * <p>foundId 允许为空：`POST /api/found/{id}/transfer` 走路径携带物品 ID；
 * `POST /api/transfer` 场景由 {@code TransferService.apply} 兜底校验非空。
 */
@Data
public class TransferDTO {

    private Long foundId;

    @NotNull(message = "请选择目标招领站")
    private Long siteId;

    @Size(max = 500, message = "备注最长 500 字")
    private String remark;
}
