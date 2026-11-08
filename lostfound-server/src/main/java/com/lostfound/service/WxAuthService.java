package com.lostfound.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lostfound.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * 微信接口服务：
 * - jscode2session：小程序 code 换 openid（既有静默登录）
 * - cgi-bin/token：缓存 access_token，供生成小程序码使用
 * - wxa/getwxacodeunlimit：生成带 scene 的小程序码，用于 Web 扫码登录
 *
 * access_token 与 openid 等敏感值不进入日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxAuthService {

    private static final String JSCODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";
    private static final String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String WXACODE_URL = "https://api.weixin.qq.com/wxa/getwxacodeunlimit";
    /** 提前刷新，避开微信凭证到期边界 */
    private static final long TOKEN_REFRESH_AHEAD_SECONDS = 300;

    @Value("${lostfound.wechat.appid}")
    private String appid;

    @Value("${lostfound.wechat.secret}")
    private String secret;

    @Value("${lostfound.qr-login.page:pages/web-login/web-login}")
    private String qrPage;

    @Value("${lostfound.qr-login.env-version:trial}")
    private String qrEnvVersion;

    @Value("${lostfound.qr-login.check-path:false}")
    private boolean qrCheckPath;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 单机内存缓存，避免每次生成二维码都换取凭证而触发微信限流 */
    private volatile String cachedAccessToken;
    private volatile long accessTokenExpiresAt;

    /**
     * 用 wx.login 的 code 换取 openid
     */
    public String code2Openid(String code) {
        String url = JSCODE2SESSION_URL
                + "?appid=" + enc(appid)
                + "&secret=" + enc(secret)
                + "&js_code=" + enc(code)
                + "&grant_type=authorization_code";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = objectMapper.readTree(response.body());
            if (node.has("errcode") && node.get("errcode").asInt() != 0) {
                log.warn("jscode2session 失败: errcode={}, errmsg={}",
                        node.path("errcode").asInt(), node.path("errmsg").asText(""));
                throw new BusinessException("微信登录失败：" + node.path("errmsg").asText("未知错误"));
            }
            String openid = node.path("openid").asText(null);
            if (openid == null || openid.isEmpty()) {
                throw new BusinessException("微信登录失败：未获取到用户标识");
            }
            return openid;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("请求微信 jscode2session 异常", e);
            throw new BusinessException("微信登录失败，请稍后重试");
        }
    }

    /** 获取小程序全局 access_token（有效期 7200s，提前 5 分钟刷新） */
    public synchronized String getAccessToken() {
        if (cachedAccessToken != null && System.currentTimeMillis() < accessTokenExpiresAt) {
            return cachedAccessToken;
        }
        if (secret == null || secret.isBlank()) {
            throw new BusinessException("未配置 WX_SECRET，无法生成登录二维码");
        }
        String url = ACCESS_TOKEN_URL + "?grant_type=client_credential&appid=" + enc(appid) + "&secret=" + enc(secret);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = objectMapper.readTree(response.body());
            String token = node.path("access_token").asText(null);
            long expiresIn = node.path("expires_in").asLong(0);
            if (token == null || token.isEmpty() || expiresIn <= 0) {
                log.warn("获取微信 access_token 失败: errcode={}", node.path("errcode").asInt(-1));
                throw new BusinessException("获取微信凭证失败，请稍后重试");
            }
            cachedAccessToken = token;
            long validSeconds = Math.max(60, expiresIn - TOKEN_REFRESH_AHEAD_SECONDS);
            accessTokenExpiresAt = System.currentTimeMillis() + validSeconds * 1000L;
            return token;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("请求微信 access_token 异常", e);
            throw new BusinessException("微信服务暂不可用，请稍后重试");
        }
    }

    /**
     * 生成带 scene 的小程序码，返回可直接用于 img src 的 data URL。
     * 微信成功时返回图片二进制，失败时返回 JSON（errcode/errmsg）。
     */
    public String getMiniProgramCode(String scene) {
        String token = getAccessToken();
        String url = WXACODE_URL + "?access_token=" + enc(token);
        Map<String, Object> payload = Map.of(
                "scene", scene,
                "page", qrPage,
                "width", 280,
                "check_path", qrCheckPath,
                "env_version", qrEnvVersion
        );
        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] bytes = response.body();
            if (bytes == null || bytes.length == 0) {
                throw new BusinessException("生成小程序码失败，请稍后重试");
            }
            if (bytes[0] == '{') {
                JsonNode node = objectMapper.readTree(bytes);
                int errcode = node.path("errcode").asInt(-1);
                log.warn("生成小程序码失败: errcode={}", errcode);
                if (errcode == 40001 || errcode == 42001) invalidateAccessToken();
                throw new BusinessException("生成小程序码失败：" + node.path("errmsg").asText("请稍后重试"));
            }
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("请求微信 getwxacodeunlimit 异常", e);
            throw new BusinessException("生成小程序码失败，请稍后重试");
        }
    }

    private synchronized void invalidateAccessToken() {
        cachedAccessToken = null;
        accessTokenExpiresAt = 0;
    }

    private String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
