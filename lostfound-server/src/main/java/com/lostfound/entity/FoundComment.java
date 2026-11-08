package com.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("found_comment")
public class FoundComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long itemId;
    private Long userId;
    private Long parentId;
    private String content;

    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
}
