package com.lostfound.dto;

import lombok.Data;

@Data
public class UpdateProfileDTO {

    private String nickname;
    private String avatar;
    private String phone;
    private String email;
}
