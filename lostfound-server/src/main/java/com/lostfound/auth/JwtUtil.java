package com.lostfound.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具
 */
@Component
public class JwtUtil {

    @Value("${lostfound.jwt.secret}")
    private String secret;

    @Value("${lostfound.jwt.expire-hours}")
    private long expireHours;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(Long userId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireHours * 3600_000L))
                .signWith(key())
                .compact();
    }

    /**
     * 解析 token，失败返回 null
     */
    @SuppressWarnings("unchecked")
    public Long parseUserId(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key()).build()
                    .parseSignedClaims(token).getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            return null;
        }
    }

    public String parseRole(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key()).build()
                    .parseSignedClaims(token).getPayload();
            return claims.get("role", String.class);
        } catch (Exception e) {
            return null;
        }
    }
}
