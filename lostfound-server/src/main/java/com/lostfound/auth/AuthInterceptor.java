package com.lostfound.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lostfound.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 登录/角色鉴权拦截器：
 * - 所有请求尝试解析 token 写入 UserContext（支持匿名浏览）
 * - 标注 @RequireLogin / @RequireRole 的接口强制校验
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 OPTIONS 预检
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }

        String token = resolveToken(request);
        if (token != null) {
            Long userId = jwtUtil.parseUserId(token);
            if (userId != null) {
                UserContext.set(userId, jwtUtil.parseRole(token));
            }
        }

        RequireLogin requireLogin = hm.getMethodAnnotation(RequireLogin.class);
        if (requireLogin == null) {
            requireLogin = hm.getBeanType().getAnnotation(RequireLogin.class);
        }
        if (requireLogin != null && UserContext.getUserId() == null) {
            reject(response, 401, "请先登录");
            return false;
        }

        RequireRole requireRole = hm.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = hm.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole != null) {
            Long userId = UserContext.getUserId();
            if (userId == null) {
                reject(response, 401, "请先登录");
                return false;
            }
            String role = UserContext.getRole();
            boolean pass = false;
            for (String r : requireRole.value()) {
                if (r.equals(role)) {
                    pass = true;
                    break;
                }
            }
            if (!pass) {
                reject(response, 403, "无权限访问");
                return false;
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private void reject(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(
                objectMapper.writeValueAsString(Result.fail(code, message)).getBytes(StandardCharsets.UTF_8));
    }
}
