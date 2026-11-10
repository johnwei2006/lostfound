package com.lostfound.controller;

import com.lostfound.auth.RequireLogin;
import com.lostfound.auth.RequireRole;
import com.lostfound.auth.UserContext;
import com.lostfound.common.PageResult;
import com.lostfound.common.Result;
import com.lostfound.dto.PublishItemDTO;
import com.lostfound.dto.ReturnDTO;
import com.lostfound.dto.SiteDTO;
import com.lostfound.dto.SiteEndorsementDTO;
import com.lostfound.entity.FoundSite;
import com.lostfound.service.ClaimService;
import com.lostfound.service.FoundSiteService;
import com.lostfound.service.ReturnService;
import com.lostfound.service.TransferService;
import com.lostfound.vo.ClaimVO;
import com.lostfound.vo.ItemVO;
import com.lostfound.vo.ReturnRecordVO;
import com.lostfound.vo.SiteStatsVO;
import com.lostfound.vo.SiteVO;
import com.lostfound.vo.TransferVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 招领站接口：公开查询 + 全局 ADMIN 站点维护 + 站点级管理后台。
 * 站点写操作的权限在 Service 层用 SitePermissionService.requireManager 校验。
 */
@RestController
@RequestMapping("/api/foundsite")
@RequiredArgsConstructor
public class FoundSiteController {

    private final FoundSiteService foundSiteService;
    private final TransferService transferService;
    private final ReturnService returnService;
    private final ClaimService claimService;

    /** 我可管理的站点（全局 ADMIN 返回全部）；字面量路径优先于 /{id} */
    @RequireLogin
    @GetMapping("/mine")
    public Result<List<FoundSite>> mine() {
        return Result.ok(foundSiteService.mine(UserContext.getUserId()));
    }

    @GetMapping("/page")
    public Result<PageResult<SiteVO>> page(@RequestParam(required = false) String keyword,
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

    /** 站内物品清单：仅站点发布或转交审批通过的物品；manage=true 需站点管理员 */
    @GetMapping("/{id}/items")
    public Result<PageResult<ItemVO>> items(@PathVariable Long id,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String sourceType,
                                            @RequestParam(defaultValue = "false") boolean manage,
                                            @RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(foundSiteService.items(id, keyword, category, status, sourceType,
                manage, page, size, UserContext.getUserId()));
    }

    @RequireLogin
    @GetMapping("/{id}/stats")
    public Result<SiteStatsVO> stats(@PathVariable Long id) {
        return Result.ok(transferService.stats(id));
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

    /** 全量覆盖式管理员名单，body 为纯用户 ID 数组 */
    @RequireRole({"ADMIN"})
    @PutMapping("/{id}/managers")
    public Result<Void> setManagers(@PathVariable Long id, @RequestBody List<Long> userIds) {
        foundSiteService.setManagers(id, userIds);
        return Result.ok();
    }

    /** 自定义背书文案：全局 ADMIN 或对应站点管理员可维护 */
    @RequireLogin
    @PutMapping("/{id}/endorsement")
    public Result<Void> updateEndorsement(@PathVariable Long id,
                                          @Valid @RequestBody SiteEndorsementDTO dto) {
        foundSiteService.updateEndorsement(id, dto);
        return Result.ok();
    }

    // ------------------ 站点级管理后台 ------------------

    /** 站点管理员代发布，直接入库无需转交审批 */
    @RequireLogin
    @PostMapping("/{id}/items")
    public Result<Long> publishItem(@PathVariable Long id, @Valid @RequestBody PublishItemDTO dto) {
        return Result.ok(foundSiteService.publishItem(id, UserContext.getUserId(), dto));
    }

    @RequireLogin
    @GetMapping("/{id}/transfers")
    public Result<PageResult<TransferVO>> transfers(@PathVariable Long id,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "10") long size) {
        return Result.ok(transferService.listForSite(id, status, keyword, page, size));
    }

    @RequireLogin
    @GetMapping("/{id}/claims")
    public Result<PageResult<ClaimVO>> claims(@PathVariable Long id,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size) {
        return Result.ok(claimService.listForSite(id, status, page, size));
    }

    @RequireLogin
    @GetMapping("/{id}/returns")
    public Result<PageResult<ReturnRecordVO>> returns(@PathVariable Long id,
                                                      @RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "10") long size) {
        return Result.ok(returnService.listForSite(id, page, size));
    }

    @RequireLogin
    @PostMapping("/{id}/returns")
    public Result<Long> registerReturn(@PathVariable Long id, @Valid @RequestBody ReturnDTO dto) {
        return Result.ok(returnService.register(id, dto, UserContext.getUserId()));
    }
}
