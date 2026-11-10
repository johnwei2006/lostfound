package com.lostfound.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SiteApplicationDTO {

    @NotBlank(message = "站点名称不能为空")
    @Size(max = 100, message = "站点名称最长 100 字")
    private String name;

    @NotBlank(message = "站点地址不能为空")
    @Size(max = 200, message = "站点地址最长 200 字")
    private String address;

    private BigDecimal longitude;
    private BigDecimal latitude;

    @Size(max = 20, message = "联系电话最长 20 字")
    private String phone;

    @Size(max = 100, message = "开放时间最长 100 字")
    private String openTime;

    @Size(max = 1000, message = "站点简介最长 1000 字")
    private String description;

    @NotBlank(message = "申请理由不能为空")
    @Size(max = 500, message = "申请理由最长 500 字")
    private String reason;

    @Size(max = 100, message = "联系方式最长 100 字")
    private String contact;
}
