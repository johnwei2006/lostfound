package com.lostfound.controller;

import com.lostfound.auth.RequireLogin;
import com.lostfound.auth.UserContext;
import com.lostfound.common.PageResult;
import com.lostfound.common.Result;
import com.lostfound.dto.ClaimDTO;
import com.lostfound.dto.ItemQuery;
import com.lostfound.dto.PublishItemDTO;
import com.lostfound.entity.Found;
import com.lostfound.service.ClaimService;
import com.lostfound.service.ItemService;
import com.lostfound.vo.ClaimVO;
import com.lostfound.vo.ItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 拾物接口
 */
@RestController
@RequestMapping("/api/found")
@RequiredArgsConstructor
public class FoundController {

    private static final String TYPE = "FOUND";

    private final ItemService itemService;
    private final ClaimService claimService;

    /** 分页列表（支持匿名浏览） */
    @GetMapping("/page")
    public Result<PageResult<ItemVO>> page(ItemQuery query) {
        return Result.ok(itemService.page(TYPE, query, UserContext.getUserId()));
    }

    /** 最新物品（首页缓存接口） */
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

    /** 上架/下架 */
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

    // ------------------ 认领流转 ------------------

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

    /** 我收到的申请（作为发布者） */
    @RequireLogin
    @GetMapping("/claims/received")
    public Result<List<ClaimVO>> receivedClaims() {
        // 简化实现：由前端按物品逐个查询，这里返回我的全部待处理申请
        return Result.ok(claimService.mine(UserContext.getUserId()));
    }

    @RequireLogin
    @PutMapping("/claims/{claimId}/review")
    public Result<Void> reviewClaim(@PathVariable Long claimId, @RequestParam boolean approve) {
        claimService.review(claimId, UserContext.getUserId(), approve);
        return Result.ok();
    }

    /** 地图数据：带坐标的物品 */
    @GetMapping("/map")
    public Result<List<ItemVO>> mapItems(@RequestParam(defaultValue = "200") int limit) {
        ItemQuery q = new ItemQuery();
        q.setSize(Math.min(limit, 500));
        q.setStatus("WAITING");
        return Result.ok(itemService.page(TYPE, q, UserContext.getUserId()).getRecords());
    }
}
