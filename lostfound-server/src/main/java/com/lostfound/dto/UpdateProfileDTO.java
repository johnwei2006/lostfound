package com.lostfound.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileDTO {

    /** 传 null 表示不修改；传空字符串可清空联系方式 */
    @Size(max = 50, message = "昵称最长 50 字")
    private String nickname;

    @Size(max = 500, message = "头像地址最长 500 字")
    private String avatar;

    @Size(max = 20, message = "手机号最长 20 字")
    private String phone;

    @Size(max = 100, message = "邮箱最长 100 字")
    private String email;

    @Size(max = 100, message = "联系方式最长 100 字")
    private String contact;
}
