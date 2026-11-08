package com.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("message")
public class Message {

    public static final String TYPE_MATCH = "MATCH";
    public static final String TYPE_CLAIM = "CLAIM";
    public static final String TYPE_SYSTEM = "SYSTEM";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String title;
    private String content;
    private String type;
    private Long relatedId;
    private Integer isRead;

    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
}
