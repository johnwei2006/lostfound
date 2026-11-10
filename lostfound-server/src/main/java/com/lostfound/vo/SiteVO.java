package com.lostfound.vo;

import com.lostfound.entity.FoundSite;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * 招领站列表视图：附带管理员数量与在站物品数量（批量统计，避免逐行查询）。
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class SiteVO extends FoundSite {

    private Long managerCount = 0L;
    private Long itemCount = 0L;
    private Boolean hasEndorsement = false;
}
