package com.lostfound.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SiteDTO {

    @NotBlank(message = "站点名称不能为空")
    private String name;

    @NotBlank(message = "站点地址不能为空")
    private String address;

    private BigDecimal longitude;
    private BigDecimal latitude;
    private String phone;
    private String openTime;
    private String description;

    @Size(max = 100, message = "背书单位最长 100 字")
    private String endorsementOrg;

    @Size(max = 500, message = "背书推荐语最长 500 字")
    private String endorsementText;
}
