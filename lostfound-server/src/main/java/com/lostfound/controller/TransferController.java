package com.lostfound.controller;

import com.lostfound.auth.RequireLogin;
import com.lostfound.auth.UserContext;
import com.lostfound.common.PageResult;
import com.lostfound.common.Result;
import com.lostfound.dto.TransferDTO;
import com.lostfound.dto.TransferReviewDTO;
import com.lostfound.service.TransferService;
import com.lostfound.vo.SourceInfoVO;
import com.lostfound.vo.TransferVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 个人拾物转交招领站：申请、撤回、我的转交、源头信息与站点审批。
 */
@RestController
@RequestMapping("/api/transfer")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    /** 我的转交（含状态时间线与后续归还进度） */
    @RequireLogin
    @GetMapping("/mine")
    public Result<PageResult<TransferVO>> mine(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size) {
        return Result.ok(transferService.mine(UserContext.getUserId(), page, size));
    }

    /** 物品源头信息（站点管理员 / 原始登记人可见） */
    @RequireLogin
    @GetMapping("/source/{foundId}")
    public Result<SourceInfoVO> source(@PathVariable Long foundId) {
        return Result.ok(transferService.sourceInfo(foundId));
    }

    @RequireLogin
    @GetMapping("/{id}")
    public Result<TransferVO> detail(@PathVariable Long id) {
        return Result.ok(transferService.detail(id));
    }

    /** 将已发布的个人拾物申请转交到招领站 */
    @RequireLogin
    @PostMapping
    public Result<Long> apply(@Valid @RequestBody TransferDTO dto) {
        return Result.ok(transferService.apply(dto.getFoundId(), dto.getSiteId(),
                UserContext.getUserId(), dto.getRemark()));
    }

    @RequireLogin
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        transferService.cancel(id, UserContext.getUserId());
        return Result.ok();
    }

    /** 站点管理员审批（通过 / 驳回 + 意见） */
    @RequireLogin
    @PutMapping("/{id}/review")
    public Result<Void> review(@PathVariable Long id, @Valid @RequestBody TransferReviewDTO dto) {
        transferService.review(id, UserContext.getUserId(), Boolean.TRUE.equals(dto.getApprove()), dto.getRemark());
        return Result.ok();
    }
}
