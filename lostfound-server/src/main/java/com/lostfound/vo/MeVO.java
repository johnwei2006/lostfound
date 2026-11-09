package com.lostfound.vo;

import com.lostfound.entity.User;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 当前登录用户信息：在 User 基础上补充站点管理范围，供 Web 端菜单与路由守卫判定。
 */
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class MeVO extends User {

    /** 可管理的招领站 ID（全局 ADMIN 为全部站点） */
    private List<Long> managedSiteIds = List.of();

    /** 是否可进入站点管理后台 */
    private Boolean siteManager = false;
}
