package com.lostfound.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 站点管理员独立登记现场归还。
 */
@Data
public class ReturnDTO {

    @NotNull(message = "请选择归还物品")
    private Long foundId;

    @NotBlank(message = "请填写领取人")
    @Size(max = 50, message = "领取人最长 50 字")
    private String receiverName;

    @Size(max = 30, message = "联系方式最长 30 字")
    private String receiverPhone;

    /** 归还凭证图片 URL */
    private List<String> evidence;

    @Size(max = 500, message = "备注最长 500 字")
    private String remark;
}
