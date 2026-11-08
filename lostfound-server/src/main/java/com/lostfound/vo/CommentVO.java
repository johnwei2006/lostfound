package com.lostfound.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CommentVO {

    private Long id;
    private Long itemId;
    private Long userId;
    private Long parentId;
    private String content;
    private String nickname;
    private String avatar;
    private LocalDateTime createTime;

    private List<CommentVO> children = new ArrayList<>();
}
