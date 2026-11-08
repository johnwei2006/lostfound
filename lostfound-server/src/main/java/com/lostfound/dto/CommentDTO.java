package com.lostfound.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommentDTO {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论最长 1000 字")
    private String content;

    /** 回复的父评论 ID，可为空 */
    private Long parentId;
}
