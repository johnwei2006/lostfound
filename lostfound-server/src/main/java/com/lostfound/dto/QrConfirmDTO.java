package com.lostfound.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 小程序确认 Web 登录的请求参数。
 * 只接受票据，不接受 userId：身份一律取当前登录态，防止冒用他人账号。
 */
@Data
public class QrConfirmDTO {

    @NotBlank(message = "ticket 不能为空")
    @Size(max = 64, message = "ticket 不合法")
    private String ticket;
}
