package com.lostfound.controller;

import com.lostfound.auth.RequireRole;
import com.lostfound.common.PageResult;
import com.lostfound.common.Result;
import com.lostfound.entity.User;
import com.lostfound.service.AdminService;
import com.lostfound.vo.AdminStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端接口（仅管理员）
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RequireRole({"ADMIN"})
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/stats")
    public Result<AdminStatsVO> stats() {
        return Result.ok(adminService.stats());
    }

    /** 物品管理（含审核状态筛选） */
    @GetMapping("/items")
    public Result<PageResult<Map<String, Object>>> items(@RequestParam(defaultValue = "FOUND") String type,
                                                         @RequestParam(required = false) String reviewStatus,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) String keyword,
                                                         @RequestParam(defaultValue = "1") long page,
                                                         @RequestParam(defaultValue = "10") long size) {
        return Result.ok(adminService.itemPage(type, reviewStatus, status, keyword, page, size));
    }

    /** 审核：approve=true 通过 */
    @PutMapping("/item/{type}/{id}/review")
    public Result<Void> review(@PathVariable String type, @PathVariable Long id,
                               @RequestParam(defaultValue = "true") boolean approve) {
        adminService.review(type.toUpperCase(), id, approve);
        return Result.ok();
    }

    @GetMapping("/users")
    public Result<PageResult<User>> users(@RequestParam(required = false) String keyword,
                                          @RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long size) {
        return Result.ok(adminService.userPage(keyword, page, size));
    }

    @PutMapping("/user/{id}/status")
    public Result<Void> changeUserStatus(@PathVariable Long id, @RequestParam boolean enable) {
        adminService.changeUserStatus(id, enable);
        return Result.ok();
    }
}
