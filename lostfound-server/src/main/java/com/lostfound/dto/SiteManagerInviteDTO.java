package com.lostfound.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class SiteManagerInviteDTO {

    /** 有效期小时数，缺省由服务端配置决定 */
    @Min(value = 1, message = "邀请码有效期至少 1 小时")
    @Max(value = 168, message = "邀请码有效期最多 168 小时")
    private Integer ttlHours;
}
