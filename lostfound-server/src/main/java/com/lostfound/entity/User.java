package com.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String nickname;
    private String avatar;
    private String phone;
    private String email;
    /** 通用联系方式（微信号/手机号/邮箱等），发布物品时自动带入 */
    private String contact;
    /** 微信小程序 openid（微信登录用户唯一标识） */
    private String openid;
    private String role;
    private Integer status;
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
