package com.lostfound.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SiteEndorsementDTO {

    @Size(max = 100, message = "背书单位最长 100 字")
    private String endorsementOrg;

    @Size(max = 500, message = "背书推荐语最长 500 字")
    private String endorsementText;
}
