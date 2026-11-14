package com.lostfound.controller;

import com.lostfound.auth.RequireLogin;
import com.lostfound.common.Result;
import com.lostfound.dto.SiteInviteAcceptDTO;
import com.lostfound.dto.SiteManagerInviteDTO;
import com.lostfound.dto.SiteManagerTransferDTO;
import com.lostfound.service.SiteManagerService;
import com.lostfound.vo.SiteInvitePreviewVO;
import com.lostfound.vo.SiteInviteVO;
import com.lostfound.vo.SiteManagerVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 招领站主/共同管理员管理。独立挂 /api/site-managers，
 * 不改公开的 GET /api/foundsite/{id}/managers 契约（Web 站点详情页仍依赖它）。
 * 所有接口要求登录，写操作在 Service 层再次按站点校验 requireManager / requireOwner。
 */
@RestController
@RequestMapping("/api/site-managers")
@RequiredArgsConstructor
public class SiteManagerController {

    private final SiteManagerService siteManagerService;

    @RequireLogin
    @GetMapping("/{siteId}")
    public Result<List<SiteManagerVO>> managers(@PathVariable Long siteId) {
        return Result.ok(siteManagerService.managers(siteId));
    }

    @RequireLogin
    @GetMapping("/{siteId}/members/{userId}")
    public Result<SiteManagerVO> detail(@PathVariable Long siteId, @PathVariable Long userId) {
        return Result.ok(siteManagerService.detail(siteId, userId));
    }

    @RequireLogin
    @PostMapping("/{siteId}/invites")
    public Result<SiteInviteVO> createInvite(@PathVariable Long siteId,
                                             @Valid @RequestBody(required = false) SiteManagerInviteDTO dto) {
        return Result.ok(siteManagerService.createInvite(siteId, dto == null ? null : dto.getTtlHours()));
    }

    @RequireLogin
    @GetMapping("/{siteId}/invites")
    public Result<List<SiteInviteVO>> invites(@PathVariable Long siteId) {
        return Result.ok(siteManagerService.invites(siteId));
    }

    @RequireLogin
    @DeleteMapping("/{siteId}/invites/{inviteId}")
    public Result<Void> revokeInvite(@PathVariable Long siteId, @PathVariable Long inviteId) {
        siteManagerService.revokeInvite(siteId, inviteId);
        return Result.ok();
    }

    /** 确认页只读预览：持有邀请码即可查看站点名与邀请人，不暴露其它用户资料 */
    @RequireLogin
    @GetMapping("/invites/preview")
    public Result<SiteInvitePreviewVO> previewInvite(@RequestParam String code) {
        return Result.ok(siteManagerService.previewInvite(code));
    }

    /** 接受邀请后返回站点 ID，小程序据此跳转到管理工作台并刷新站点列表 */
    @RequireLogin
    @PostMapping("/invites/accept")
    public Result<Long> acceptInvite(@Valid @RequestBody SiteInviteAcceptDTO dto) {
        return Result.ok(siteManagerService.acceptInvite(dto.getCode()));
    }

    @RequireLogin
    @DeleteMapping("/{siteId}/members/{userId}")
    public Result<Void> removeManager(@PathVariable Long siteId, @PathVariable Long userId) {
        siteManagerService.removeManager(siteId, userId);
        return Result.ok();
    }

    @RequireLogin
    @PostMapping("/{siteId}/transfer")
    public Result<Void> transferOwner(@PathVariable Long siteId,
                                      @Valid @RequestBody SiteManagerTransferDTO dto) {
        siteManagerService.transferOwner(siteId, dto.getTargetUserId());
        return Result.ok();
    }
}
