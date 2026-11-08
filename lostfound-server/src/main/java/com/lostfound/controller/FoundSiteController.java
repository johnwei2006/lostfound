package com.lostfound.controller;

import com.lostfound.auth.RequireRole;
import com.lostfound.auth.UserContext;
import com.lostfound.common.PageResult;
import com.lostfound.common.Result;
import com.lostfound.dto.SiteDTO;
import com.lostfound.entity.FoundSite;
import com.lostfound.service.FoundSiteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 招领站接口
 */
@RestController
@RequestMapping("/api/foundsite")
@RequiredArgsConstructor
public class FoundSiteController {

    private final FoundSiteService foundSiteService;

    @GetMapping("/page")
    public Result<PageResult<FoundSite>> page(@RequestParam(required = false) String keyword,
                                              @RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size) {
        return Result.ok(foundSiteService.page(keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<FoundSite> detail(@PathVariable Long id) {
        return Result.ok(foundSiteService.detail(id));
    }

    @GetMapping("/{id}/managers")
    public Result<List<Map<String, Object>>> managers(@PathVariable Long id) {
        return Result.ok(foundSiteService.managers(id));
    }

    @GetMapping("/{id}/items")
    public Result<PageResult<?>> items(@PathVariable Long id,
                                       @RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "10") long size) {
        return Result.ok(foundSiteService.items(id, page, size, UserContext.getUserId()));
    }

    @RequireRole({"ADMIN"})
    @PostMapping
    public Result<Long> create(@Valid @RequestBody SiteDTO dto) {
        return Result.ok(foundSiteService.create(dto));
    }

    @RequireRole({"ADMIN"})
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SiteDTO dto) {
        foundSiteService.update(id, dto);
        return Result.ok();
    }

    @RequireRole({"ADMIN"})
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        foundSiteService.delete(id);
        return Result.ok();
    }

    @RequireRole({"ADMIN"})
    @PutMapping("/{id}/managers")
    public Result<Void> setManagers(@PathVariable Long id, @RequestBody List<Long> userIds) {
        foundSiteService.setManagers(id, userIds);
        return Result.ok();
    }
}
