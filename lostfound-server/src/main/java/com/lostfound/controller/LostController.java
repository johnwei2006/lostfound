package com.lostfound.controller;

import com.lostfound.auth.RequireLogin;
import com.lostfound.auth.UserContext;
import com.lostfound.common.PageResult;
import com.lostfound.common.Result;
import com.lostfound.dto.ClaimDTO;
import com.lostfound.dto.ItemQuery;
import com.lostfound.dto.PublishItemDTO;
import com.lostfound.service.ClaimService;
import com.lostfound.service.ItemService;
import com.lostfound.vo.ClaimVO;
import com.lostfound.vo.ItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 失物接口
 */
@RestController
@RequestMapping("/api/lost")
@RequiredArgsConstructor
public class LostController {

    private static final String TYPE = "LOST";

    /** 与 MybatisPlusConfig.maxLimit 对齐，避免请求 300 条却只拿到 100 条 */
    @Value("${lostfound.perf.map-max-limit:100}")
    private int mapMaxLimit;

    private final ItemService itemService;
    private final ClaimService claimService;

    @GetMapping("/page")
    public Result<PageResult<ItemVO>> page(ItemQuery query) {
        return Result.ok(itemService.page(TYPE, query, UserContext.getUserId()));
    }

    @GetMapping("/latest")
    public Result<List<ItemVO>> latest(@RequestParam(defaultValue = "8") int limit) {
        return Result.ok(itemService.latest(TYPE, Math.min(limit, 20), UserContext.getUserId()));
    }

    @GetMapping("/{id}")
    public Result<ItemVO> detail(@PathVariable Long id) {
        return Result.ok(itemService.detail(TYPE, id, UserContext.getUserId()));
    }

    @RequireLogin
    @PostMapping
    public Result<Long> publish(@Valid @RequestBody PublishItemDTO dto) {
        return Result.ok(itemService.publish(TYPE, UserContext.getUserId(), dto));
    }

    @RequireLogin
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PublishItemDTO dto) {
        itemService.update(TYPE, id, UserContext.getUserId(), dto);
        return Result.ok();
    }

    @RequireLogin
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        itemService.delete(TYPE, id, UserContext.getUserId(), UserContext.isAdmin());
        return Result.ok();
    }

    @RequireLogin
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam String status) {
        itemService.changeStatus(TYPE, id, UserContext.getUserId(), UserContext.isAdmin(), status);
        return Result.ok();
    }

    @RequireLogin
    @GetMapping("/mine")
    public Result<PageResult<ItemVO>> mine(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size) {
        return Result.ok(itemService.mine(TYPE, UserContext.getUserId(), page, size));
    }

    /** 有人提供线索/申请归还 */
    @RequireLogin
    @PostMapping("/{id}/claim")
    public Result<Long> claim(@PathVariable Long id, @Valid @RequestBody ClaimDTO dto) {
        return Result.ok(claimService.submit(TYPE, id, UserContext.getUserId(), dto));
    }

    @RequireLogin
    @GetMapping("/{id}/claims")
    public Result<List<ClaimVO>> claims(@PathVariable Long id) {
        return Result.ok(claimService.listByItem(TYPE, id, UserContext.getUserId(), UserContext.isAdmin()));
    }

    @RequireLogin
    @PutMapping("/claims/{claimId}/review")
    public Result<Void> reviewClaim(@PathVariable Long claimId, @RequestParam boolean approve) {
        claimService.review(claimId, UserContext.getUserId(), approve);
        return Result.ok();
    }

    @GetMapping("/map")
    public Result<List<ItemVO>> mapItems(@RequestParam(defaultValue = "200") int limit) {
        ItemQuery q = new ItemQuery();
        q.setSize(Math.min(limit, mapMaxLimit));
        q.setStatus("WAITING");
        q.enableMapMode();
        return Result.ok(itemService.page(TYPE, q, UserContext.getUserId()).getRecords());
    }
}
