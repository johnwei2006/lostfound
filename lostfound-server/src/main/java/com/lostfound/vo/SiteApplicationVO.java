package com.lostfound.vo;

import com.lostfound.entity.SiteApplication;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class SiteApplicationVO extends SiteApplication {

    private String applicantNickname;
    private String statusLabel;
}
