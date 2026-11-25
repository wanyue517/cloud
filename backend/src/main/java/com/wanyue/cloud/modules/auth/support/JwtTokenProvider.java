package com.wanyue.cloud.modules.auth.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.io.AbstractDeserializer;
import java.io.Reader;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.AbstractSerializer;
import io.jsonwebtoken.io.Serializer;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import java.io.OutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.Map;

/** 负责签发访问令牌；不承担登录校验、会话保存或刷新令牌职责。 */
@Component
public class JwtTokenProvider {
    private final SecretKey signingKey;
    private final String issuer;
    private final long accessTokenTtlSeconds;
    private final Serializer<Map<String, ?>> serializer;
    private final ObjectMapper objectMapper;

    public JwtTokenProvider(@Value("${cloud.auth.jwt.secret}") String base64Secret,
                            @Value("${cloud.auth.jwt.issuer:cloudlane}") String issuer,
                            @Value("${cloud.auth.jwt.access-token-ttl-seconds:900}") long accessTokenTtlSeconds,
                            ObjectMapper objectMapper) {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT签发者不能为空");
        }
        if (accessTokenTtlSeconds < 60 || accessTokenTtlSeconds > 3600) {
            throw new IllegalArgumentException("JWT访问令牌有效期必须在60到3600秒之间");
        }
        try {
            // 配置为Base64编码的随机密钥，解码后至少32字节；无效配置在启动时直接失败。
            this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        } catch (RuntimeException e) {
            // 不把密钥内容写入异常或日志。
            throw new IllegalArgumentException("JWT密钥无效，请配置Base64编码且解码后至少32字节的随机密钥");
        }
        this.issuer = issuer;
        this.objectMapper = objectMapper;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        // JJWT默认JSON扩展基于Jackson 2，这里通过其标准SPI复用项目已有的Jackson 3。
        this.serializer = new AbstractSerializer<>() {
            @Override
            protected void doSerialize(Map<String, ?> value, OutputStream output) throws IOException {
                output.write(objectMapper.writeValueAsBytes(value));
            }
        };
    }

    public String createAccessToken(Long userId, Integer sessionVersion) {
        if (userId == null || userId <= 0 || sessionVersion == null || sessionVersion < 0) {
            throw new IllegalArgumentException("JWT用户ID或会话版本无效");
        }
        Instant now = Instant.now();
        // JWT负载可以被读取，只放用户标识和鉴权版本，不放密码、密钥或个人资料。
        return Jwts.builder()
                .json(serializer)
                .issuer(issuer)
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTokenTtlSeconds)))
                .claim("tokenType", "access")
                .claim("sessionVersion", sessionVersion)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public Claims verify(String token) {
        AbstractDeserializer<Map<String, ?>> deserializer = new AbstractDeserializer<>() {
            @Override
            @SuppressWarnings("unchecked")
            protected Map<String, ?> doDeserialize(Reader reader) {
                return objectMapper.readValue(reader, Map.class);
            }
        };
        Claims claims = Jwts.parser().json(deserializer).verifyWith(signingKey)
                .requireIssuer(issuer).require("tokenType", "access").build()
                .parseSignedClaims(token).getPayload();
        if (claims.getExpiration() == null || Long.parseLong(claims.getSubject()) <= 0
                || claims.get("sessionVersion", Integer.class) == null) {
            throw new IllegalArgumentException("无效访问令牌");
        }
        return claims;
    }
}
