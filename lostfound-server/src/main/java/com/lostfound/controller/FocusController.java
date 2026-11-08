package com.lostfound.controller;

import com.lostfound.auth.RequireLogin;
import com.lostfound.auth.UserContext;
import com.lostfound.common.Result;
import com.lostfound.service.FocusService;
import com.lostfound.vo.ItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 关注/收藏接口（type = FOUND / LOST）
 */
@RestController
@RequestMapping("/api/focus")
@RequiredArgsConstructor
public class FocusController {

    private final FocusService focusService;

    @RequireLogin
    @PostMapping("/{type}/{itemId}")
    public Result<Void> focus(@PathVariable String type, @PathVariable Long itemId) {
        focusService.focus(type.toUpperCase(), itemId, UserContext.getUserId());
        return Result.ok();
    }

    @RequireLogin
    @DeleteMapping("/{type}/{itemId}")
    public Result<Void> unfocus(@PathVariable String type, @PathVariable Long itemId) {
        focusService.unfocus(type.toUpperCase(), itemId, UserContext.getUserId());
        return Result.ok();
    }

    @GetMapping("/{type}/{itemId}/status")
    public Result<Boolean> isFocused(@PathVariable String type, @PathVariable Long itemId) {
        return Result.ok(focusService.isFocused(type.toUpperCase(), itemId, UserContext.getUserId()));
    }

    @RequireLogin
    @GetMapping("/{type}/mine")
    public Result<?> myFocus(@PathVariable String type,
                             @RequestParam(defaultValue = "1") long page,
                             @RequestParam(defaultValue = "10") long size) {
        return Result.ok(focusService.myFocus(type.toUpperCase(), UserContext.getUserId(), page, size));
    }
}
