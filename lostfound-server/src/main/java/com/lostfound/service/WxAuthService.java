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

/**
 * 微信小程序登录服务：
 * 以 jscode2session 换取 openid，实现"微信一键登录"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxAuthService {

    private static final String JSCODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";

    @Value("${lostfound.wechat.appid}")
    private String appid;

    @Value("${lostfound.wechat.secret}")
    private String secret;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 用 wx.login 的 code 换取 openid
     */
    public String code2Openid(String code) {
        String url = JSCODE2SESSION_URL
                + "?appid=" + URLEncoder.encode(appid, StandardCharsets.UTF_8)
                + "&secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8)
                + "&js_code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
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
                log.warn("jscode2session 失败: {}", response.body());
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
}
