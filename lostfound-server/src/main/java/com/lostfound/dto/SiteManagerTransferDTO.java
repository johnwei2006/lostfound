package com.lostfound.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SiteManagerTransferDTO {

    /** 受让方必须是该站点现有的共同管理员 */
    @NotNull(message = "请选择要转让的管理员")
    private Long targetUserId;
}
