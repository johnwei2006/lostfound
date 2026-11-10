package com.lostfound.controller;

import com.lostfound.auth.RequireLogin;
import com.lostfound.auth.RequireRole;
import com.lostfound.auth.UserContext;
import com.lostfound.common.PageResult;
import com.lostfound.common.Result;
import com.lostfound.dto.SiteApplicationDTO;
import com.lostfound.dto.SiteApplicationReviewDTO;
import com.lostfound.service.SiteApplicationService;
import com.lostfound.vo.SiteApplicationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 建站申请独立放在 /api/site-applications，避免与 /api/foundsite/{id} 字面量路径冲突。
 */
@RestController
@RequestMapping("/api/site-applications")
@RequiredArgsConstructor
public class SiteApplicationController {

    private final SiteApplicationService siteApplicationService;

    @RequireLogin
    @PostMapping
    public Result<Long> submit(@Valid @RequestBody SiteApplicationDTO dto) {
        return Result.ok(siteApplicationService.submit(UserContext.getUserId(), dto));
    }

    @RequireLogin
    @GetMapping("/mine")
    public Result<List<SiteApplicationVO>> mine() {
        return Result.ok(siteApplicationService.mine(UserContext.getUserId()));
    }

    @RequireRole({"ADMIN"})
    @GetMapping("/page")
    public Result<PageResult<SiteApplicationVO>> page(@RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "10") long size) {
        return Result.ok(siteApplicationService.page(status, keyword, page, size));
    }

    @RequireRole({"ADMIN"})
    @PutMapping("/{id}/review")
    public Result<Long> review(@PathVariable Long id,
                               @Valid @RequestBody SiteApplicationReviewDTO dto) {
        return Result.ok(siteApplicationService.review(id, dto, UserContext.getUserId()));
    }

    @RequireLogin
    @DeleteMapping("/{id}")
    public Result<Void> withdraw(@PathVariable Long id) {
        siteApplicationService.withdraw(id, UserContext.getUserId());
        return Result.ok();
    }
}
