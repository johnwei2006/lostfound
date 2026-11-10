package com.lostfound.controller;

import com.lostfound.auth.RequireLogin;
import com.lostfound.auth.UserContext;
import com.lostfound.common.Result;
import com.lostfound.service.MatchService;
import com.lostfound.vo.MatchVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 智能匹配接口
 */
@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
@RequireLogin
public class MatchController {

    private final MatchService matchService;

    /** 与我相关的匹配推荐 */
    @GetMapping("/mine")
    public Result<List<MatchVO>> mine() {
        return Result.ok(matchService.myMatches(UserContext.getUserId()));
    }

    /** 采纳/忽略匹配 */
    @PutMapping("/{id}/status")
    public Result<Void> setStatus(@PathVariable Long id, @RequestParam String status) {
        matchService.setStatus(id, UserContext.getUserId(), status);
        return Result.ok();
    }

    /** ADMIN：对存量 WAITING 物品按当前算法重跑匹配（幂等，旧记录保留，不重复通知） */
    @PostMapping("/rebuild")
    public Result<Map<String, Integer>> rebuild() {
        return Result.ok(matchService.rebuildAll());
    }
}
