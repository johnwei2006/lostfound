package com.lostfound.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * 分页结果封装
 */
@Data
public class PageResult<T> {

    private long total;
    private long pages;
    private long current;
    private long size;
    private List<T> records;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> r = new PageResult<>();
        r.total = page.getTotal();
        r.pages = page.getPages();
        r.current = page.getCurrent();
        r.size = page.getSize();
        r.records = page.getRecords();
        return r;
    }
}
