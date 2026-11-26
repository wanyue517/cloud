package com.wanyue.cloud.modules.auth.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

@Service
public class WebSessionService {
    private final JdbcTemplate db;
    private final JwtTokenProvider tokens;
    private final byte[] key;

    public WebSessionService(JdbcTemplate db, JwtTokenProvider tokens, @Value("${cloud.auth.jwt.secret}") String secret) {
        this.db = db;
        this.tokens = tokens;
        this.key = Base64.getDecoder().decode(secret);
    }

    public record SessionTokens(String accessToken, long expiresIn, String refreshToken) {}

    public record DeviceSession(
            String id,
            String deviceName,
            String clientType,
            String ipAddress,
            String createdAt,
            String lastUsedAt,
            String expiresAt,
            boolean current) {}

    private String random() {
        byte[] value = new byte[32];
        new SecureRandom().nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String hash(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public String start(long user) {
        return start(user, null, null);
    }

    @Transactional
    public String start(long user, String userAgent, String ipAddress) {
        String refresh = random();
        int version = db.queryForObject("SELECT session_version FROM auth_user_security WHERE user_id=? AND deleted=0", Integer.class, user);
        String ua = userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 512));
        String ip = ipAddress == null ? null : ipAddress.substring(0, Math.min(ipAddress.length(), 45));
        String deviceName = deriveDeviceName(ua);
        db.update(
                "INSERT INTO auth_session(id,user_id,refresh_token_hash,refresh_expires_at,device_name,client_type,user_agent,ip_address,last_used_at,session_version) "
                        + "VALUES (?,?,?,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 30 DAY),?,'WEB',?,?,UTC_TIMESTAMP(3),?)",
                IdWorker.getId(), user, hash(refresh), deviceName, ua, ip, version);
        return refresh;
    }

    @Transactional
    public SessionTokens refresh(String refresh) {
        if (refresh == null || refresh.length() > 200) throw BizException.unauthorized("会话已失效");
        var sessions = db.queryForList(
                "SELECT s.id,s.user_id,s.session_version FROM auth_session s JOIN sys_user u ON u.id=s.user_id JOIN auth_user_security sec ON sec.user_id=u.id "
                        + "WHERE s.refresh_token_hash=? AND s.deleted=0 AND s.revoked_at IS NULL AND s.refresh_expires_at>UTC_TIMESTAMP(3) "
                        + "AND u.status=1 AND u.deleted=0 AND sec.deleted=0 AND sec.session_version=s.session_version FOR UPDATE",
                hash(refresh));
        if (sessions.isEmpty()) throw BizException.unauthorized("会话已失效");
        var session = sessions.get(0);
        String next = random();
        long user = ((Number) session.get("user_id")).longValue();
        int version = ((Number) session.get("session_version")).intValue();
        db.update(
                "UPDATE auth_session SET refresh_token_hash=?,rotation_count=rotation_count+1,last_used_at=UTC_TIMESTAMP(3),last_rotated_at=UTC_TIMESTAMP(3) WHERE id=?",
                hash(next), session.get("id"));
        return new SessionTokens(tokens.createAccessToken(user, version), tokens.getAccessTokenTtlSeconds(), next);
    }

    public List<DeviceSession> listActive(long user, String currentRefreshToken) {
        String currentHash = (currentRefreshToken != null && !currentRefreshToken.isBlank() && currentRefreshToken.length() <= 200)
                ? hash(currentRefreshToken)
                : null;
        var rows = db.queryForList(
                "SELECT CAST(s.id AS CHAR) id, s.device_name deviceName, s.client_type clientType, s.ip_address ipAddress, "
                        + "s.created_at createdAt, s.last_used_at lastUsedAt, s.refresh_expires_at expiresAt, s.refresh_token_hash tokenHash "
                        + "FROM auth_session s JOIN auth_user_security sec ON sec.user_id=s.user_id "
                        + "WHERE s.user_id=? AND s.deleted=0 AND s.revoked_at IS NULL AND s.refresh_expires_at>UTC_TIMESTAMP(3) "
                        + "AND sec.deleted=0 AND sec.session_version=s.session_version "
                        + "ORDER BY COALESCE(s.last_used_at, s.created_at) DESC, s.id DESC",
                user);
        List<DeviceSession> result = new ArrayList<>(rows.size());
        for (var row : rows) {
            boolean isCurrent = currentHash != null && currentHash.equals(row.get("tokenHash"));
            result.add(new DeviceSession(
                    String.valueOf(row.get("id")),
                    row.get("deviceName") == null || String.valueOf(row.get("deviceName")).isBlank() ? "Web 浏览器" : String.valueOf(row.get("deviceName")),
                    row.get("clientType") == null ? "WEB" : String.valueOf(row.get("clientType")),
                    row.get("ipAddress") == null ? "" : String.valueOf(row.get("ipAddress")),
                    formatTime(row.get("createdAt")),
                    formatTime(row.get("lastUsedAt")),
                    formatTime(row.get("expiresAt")),
                    isCurrent
            ));
        }
        return result;
    }

    @Transactional
    public void revokeSession(long user, long sessionId) {
        int updated = db.update(
                "UPDATE auth_session SET revoked_at=UTC_TIMESTAMP(3), revoke_reason='USER_REVOKE' "
                        + "WHERE id=? AND user_id=? AND deleted=0 AND revoked_at IS NULL",
                sessionId, user);
        if (updated != 1) {
            throw BizException.notFound("设备会话不存在或已失效");
        }
    }

    @Transactional
    public void logout(long user) {
        db.update("UPDATE auth_user_security SET session_version=session_version+1 WHERE user_id=? AND deleted=0", user);
        db.update("UPDATE auth_session SET revoked_at=UTC_TIMESTAMP(3),revoke_reason='LOGOUT' WHERE user_id=? AND revoked_at IS NULL", user);
    }

    private static String deriveDeviceName(String ua) {
        if (ua == null || ua.isBlank()) return "Web 浏览器";
        String lower = ua.toLowerCase(Locale.ROOT);
        String browser = lower.contains("edg") ? "Edge"
                : lower.contains("chrome") ? "Chrome"
                : lower.contains("firefox") ? "Firefox"
                : lower.contains("safari") ? "Safari" : "浏览器";
        String os = lower.contains("windows") ? "Windows"
                : lower.contains("mac os") ? "macOS"
                : lower.contains("linux") ? "Linux"
                : lower.contains("android") ? "Android"
                : lower.contains("iphone") || lower.contains("ipad") ? "iOS" : "";
        return os.isEmpty() ? browser : os + " · " + browser;
    }

    private static String formatTime(Object value) {
        return value == null ? "" : value.toString();
    }
}
