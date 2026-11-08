package com.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lost_focus")
public class LostFocus {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long itemId;
    private Long userId;
    private LocalDateTime createTime;
}
