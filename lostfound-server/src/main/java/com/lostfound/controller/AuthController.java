package com.lostfound.controller;

import com.lostfound.auth.RequireLogin;
import com.lostfound.auth.UserContext;
import com.lostfound.common.Result;
import com.lostfound.dto.ChangePasswordDTO;
import com.lostfound.dto.LoginDTO;
import com.lostfound.dto.RegisterDTO;
import com.lostfound.dto.UpdateProfileDTO;
import com.lostfound.dto.WxLoginDTO;
import com.lostfound.entity.User;
import com.lostfound.service.AuthService;
import com.lostfound.service.LogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LogService logService;

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterDTO dto, HttpServletRequest request) {
        Map<String, Object> data = authService.register(dto);
        logService.record(((User) data.get("user")).getId(), "REGISTER", dto.getUsername(), request.getRemoteAddr());
        return Result.ok(data);
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        Map<String, Object> data = authService.login(dto);
        logService.record(((User) data.get("user")).getId(), "LOGIN", dto.getUsername(), request.getRemoteAddr());
        return Result.ok(data);
    }

    /**
     * 微信小程序登录（wx.login 的 code 换 token）
     */
    @PostMapping("/wx-login")
    public Result<Map<String, Object>> wxLogin(@Valid @RequestBody WxLoginDTO dto, HttpServletRequest request) {
        Map<String, Object> data = authService.wxLogin(dto);
        logService.record(((User) data.get("user")).getId(), "WX_LOGIN", "weixin", request.getRemoteAddr());
        return Result.ok(data);
    }

    @RequireLogin
    @GetMapping("/me")
    public Result<User> me() {
        return Result.ok(authService.profile(UserContext.getUserId()));
    }

    @RequireLogin
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody UpdateProfileDTO dto) {
        authService.updateProfile(UserContext.getUserId(), dto);
        return Result.ok();
    }

    @RequireLogin
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        authService.changePassword(UserContext.getUserId(), dto);
        return Result.ok();
    }
}
