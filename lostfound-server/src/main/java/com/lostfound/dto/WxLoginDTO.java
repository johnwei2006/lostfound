package com.lostfound.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信小程序登录参数
 */
@Data
public class WxLoginDTO {

    /** wx.login 获取的临时登录凭证 */
    @NotBlank(message = "code 不能为空")
    private String code;
}
