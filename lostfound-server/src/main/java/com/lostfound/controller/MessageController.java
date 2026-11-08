package com.lostfound.controller;

import com.lostfound.auth.RequireLogin;
import com.lostfound.auth.UserContext;
import com.lostfound.common.PageResult;
import com.lostfound.common.Result;
import com.lostfound.entity.Message;
import com.lostfound.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 站内消息接口
 */
@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
@RequireLogin
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/page")
    public Result<PageResult<Message>> page(@RequestParam(required = false) String type,
                                            @RequestParam(required = false) Integer isRead,
                                            @RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(messageService.page(UserContext.getUserId(), type, isRead, page, size));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.ok(messageService.unreadCount(UserContext.getUserId()));
    }

    @PutMapping("/{id}/read")
    public Result<Void> read(@PathVariable Long id) {
        messageService.read(id, UserContext.getUserId());
        return Result.ok();
    }

    @PutMapping("/read-all")
    public Result<Void> readAll() {
        messageService.readAll(UserContext.getUserId());
        return Result.ok();
    }
}
