package com.lostfound.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ClaimVO {

    private Long id;
    private String itemType;
    private Long itemId;
    private String itemTitle;
    private Long userId;
    private String nickname;
    private String avatar;
    private String description;
    private String contact;
    private String status;
    private LocalDateTime createTime;
}
