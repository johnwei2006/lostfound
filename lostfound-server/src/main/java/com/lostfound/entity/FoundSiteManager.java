package com.lostfound.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("foundsite_manager")
public class FoundSiteManager {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long siteId;
    private Long userId;
    private LocalDateTime createTime;
}
