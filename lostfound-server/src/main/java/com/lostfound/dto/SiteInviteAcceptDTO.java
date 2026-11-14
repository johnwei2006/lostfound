package com.lostfound.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SiteInviteAcceptDTO {

    @NotBlank(message = "请输入邀请码")
    @Size(max = 32, message = "邀请码格式不正确")
    private String code;
}
