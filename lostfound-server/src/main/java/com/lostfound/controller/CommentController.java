package com.lostfound.controller;

import com.lostfound.auth.RequireLogin;
import com.lostfound.auth.UserContext;
import com.lostfound.common.Result;
import com.lostfound.dto.CommentDTO;
import com.lostfound.service.CommentService;
import com.lostfound.vo.CommentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评论接口（type = FOUND / LOST）
 */
@RestController
@RequestMapping("/api/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/{type}/{itemId}")
    public Result<List<CommentVO>> list(@PathVariable String type, @PathVariable Long itemId) {
        return Result.ok(commentService.list(type.toUpperCase(), itemId));
    }

    @RequireLogin
    @PostMapping("/{type}/{itemId}")
    public Result<Long> add(@PathVariable String type, @PathVariable Long itemId,
                            @Valid @RequestBody CommentDTO dto) {
        return Result.ok(commentService.add(type.toUpperCase(), itemId, UserContext.getUserId(), dto));
    }

    @RequireLogin
    @DeleteMapping("/{type}/{id}")
    public Result<Void> delete(@PathVariable String type, @PathVariable Long id) {
        commentService.delete(type.toUpperCase(), id, UserContext.getUserId(), UserContext.isAdmin());
        return Result.ok();
    }
}
