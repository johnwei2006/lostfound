package com.lostfound.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lostfound.common.PageResult;
import com.lostfound.entity.FoundFocus;
import com.lostfound.entity.LostFocus;
import com.lostfound.mapper.FoundFocusMapper;
import com.lostfound.mapper.LostFocusMapper;
import com.lostfound.vo.ItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 关注/收藏服务
 */
@Service
@RequiredArgsConstructor
public class FocusService {

    private final FoundFocusMapper foundFocusMapper;
    private final LostFocusMapper lostFocusMapper;
    private final ItemService itemService;

    public boolean focus(String type, Long itemId, Long userId) {
        if ("FOUND".equals(type)) {
            itemService.requireFound(itemId);
            if (foundFocusMapper.selectCount(new QueryWrapper<FoundFocus>()
                    .eq("item_id", itemId).eq("user_id", userId)) > 0) {
                return true;
            }
            FoundFocus f = new FoundFocus();
            f.setItemId(itemId);
            f.setUserId(userId);
            f.setCreateTime(LocalDateTime.now());
            foundFocusMapper.insert(f);
        } else {
            itemService.requireLost(itemId);
            if (lostFocusMapper.selectCount(new QueryWrapper<LostFocus>()
                    .eq("item_id", itemId).eq("user_id", userId)) > 0) {
                return true;
            }
            LostFocus f = new LostFocus();
            f.setItemId(itemId);
            f.setUserId(userId);
            f.setCreateTime(LocalDateTime.now());
            lostFocusMapper.insert(f);
        }
        return true;
    }

    public boolean unfocus(String type, Long itemId, Long userId) {
        if ("FOUND".equals(type)) {
            foundFocusMapper.delete(new QueryWrapper<FoundFocus>()
                    .eq("item_id", itemId).eq("user_id", userId));
        } else {
            lostFocusMapper.delete(new QueryWrapper<LostFocus>()
                    .eq("item_id", itemId).eq("user_id", userId));
        }
        return true;
    }

    public boolean isFocused(String type, Long itemId, Long userId) {
        if (userId == null) return false;
        if ("FOUND".equals(type)) {
            return foundFocusMapper.selectCount(new QueryWrapper<FoundFocus>()
                    .eq("item_id", itemId).eq("user_id", userId)) > 0;
        }
        return lostFocusMapper.selectCount(new QueryWrapper<LostFocus>()
                .eq("item_id", itemId).eq("user_id", userId)) > 0;
    }

    /** 我的关注列表 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public PageResult<ItemVO> myFocus(String type, Long userId, long page, long size) {
        List<Long> itemIds;
        long total;
        long pages;
        long current;
        if ("FOUND".equals(type)) {
            Page<FoundFocus> p = foundFocusMapper.selectPage(new Page<>(page, size),
                    new QueryWrapper<FoundFocus>().eq("user_id", userId).orderByDesc("create_time"));
            itemIds = p.getRecords().stream().map(FoundFocus::getItemId).toList();
            total = p.getTotal();
            pages = p.getPages();
            current = p.getCurrent();
        } else {
            Page<LostFocus> p = lostFocusMapper.selectPage(new Page<>(page, size),
                    new QueryWrapper<LostFocus>().eq("user_id", userId).orderByDesc("create_time"));
            itemIds = p.getRecords().stream().map(LostFocus::getItemId).toList();
            total = p.getTotal();
            pages = p.getPages();
            current = p.getCurrent();
        }
        List<ItemVO> records = new java.util.ArrayList<>(itemService.listByIds(type, itemIds, userId));
        // 按关注时间排序（listByIds 按 ID 升序返回）
        PageResult<ItemVO> result = new PageResult<>();
        result.setTotal(total);
        result.setPages(pages);
        result.setCurrent(current);
        result.setSize(size);
        result.setRecords(records);
        return result;
    }
}
