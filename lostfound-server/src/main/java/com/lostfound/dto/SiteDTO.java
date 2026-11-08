package com.lostfound.dto;

import jakarta.validation.constraints.NotBlank;
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
}
