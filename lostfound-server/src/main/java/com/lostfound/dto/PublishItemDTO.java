package com.lostfound.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 发布/编辑失物或拾物
 */
@Data
public class PublishItemDTO {

    @NotBlank(message = "物品名称不能为空")
    @Size(max = 100, message = "物品名称最长 100 字")
    private String title;

    /** 类别白名单见 com.lostfound.common.ItemCategory，由 ItemService 在写入前校验 */
    @NotBlank(message = "物品类别不能为空")
    private String category;

    @Size(max = 2000, message = "描述最长 2000 字")
    private String description;

    private List<String> images;

    private String province;
    private String city;
    private String district;
    private String address;
    private BigDecimal longitude;
    private BigDecimal latitude;

    /** 拾物=拾获时间；失物=丢失时间 */
    private LocalDateTime itemTime;

    private String contact;

    /** 拾物可关联招领站 */
    private Long siteId;
}
