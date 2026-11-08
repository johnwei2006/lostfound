package com.lostfound.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ClaimDTO {

    @NotBlank(message = "认领凭证描述不能为空")
    private String description;

    private String contact;
}
