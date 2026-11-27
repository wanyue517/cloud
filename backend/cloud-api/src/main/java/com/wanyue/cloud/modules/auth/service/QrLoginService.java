package com.wanyue.cloud.modules.auth.service;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QrLoginService {
    public static final long TTL_SECONDS = 120L;
    private static final String REDIS_PREFIX = "cloudlane:qr:login:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate db;
    private final JwtTokenProvider jwtTokenProvider;
    private final WebSessionService webSessions;

    /** 无 Redis 环境下（例如单测）自动降级的内存 TTL 存储。 */
    private final ConcurrentHashMap<String, TicketState> localStore = new ConcurrentHashMap<>();

    public QrLoginService(
            ObjectProvider<StringRedisTemplate> redisProvider,
            ObjectMapper objectMapper,
            JdbcTemplate db,
            JwtTokenProvider jwtTokenProvider,
            WebSessionService webSessions) {
        this.redisProvider = redisProvider;
        this.objectMapper = objectMapper;
        this.db = db;
        this.jwtTokenProvider = jwtTokenProvider;
        this.webSessions = webSessions;
    }

    public record TicketState(String status, Long userId, long expiresAtEpochMs) {
        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAtEpochMs;
        }
    }

    public record QrCreateResult(String qrId, long expiresInSeconds, String expiresAt) {}

    public record QrPollResult(
            String status,
            String accessToken,
            Long expiresInSeconds,
            String displayName,
            String refreshToken) {}

    private static String generateQrId() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private TicketState readTicket(String qrId) {
        if (qrId == null || qrId.isBlank() || qrId.length() > 64) return null;
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                String raw = redis.opsForValue().get(REDIS_PREFIX + qrId);
                if (raw == null) return null;
                TicketState state = objectMapper.readValue(raw, TicketState.class);
                if (state.isExpired()) {
                    redis.delete(REDIS_PREFIX + qrId);
                    return null;
                }
                return state;
            } catch (Exception ignored) {
                // fall through to local store if Redis unavailable
            }
        }
        TicketState state = localStore.get(qrId);
        if (state == null || state.isExpired()) {
            localStore.remove(qrId);
            return null;
        }
        return state;
    }

    private void writeTicket(String qrId, TicketState state) {
        long remainingMs = Math.max(1000L, state.expiresAtEpochMs() - System.currentTimeMillis());
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                String json = objectMapper.writeValueAsString(state);
                redis.opsForValue().set(REDIS_PREFIX + qrId, json, Duration.ofMillis(remainingMs));
                return;
            } catch (Exception ignored) {
                // fall back to local store
            }
        }
        localStore.put(qrId, state);
    }

    private TicketState consumeTicket(String qrId) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis != null) {
            try {
                String raw = redis.opsForValue().getAndDelete(REDIS_PREFIX + qrId);
                if (raw != null) {
                    TicketState state = objectMapper.readValue(raw, TicketState.class);
                    return state.isExpired() ? null : state;
                }
                return null;
            } catch (Exception ignored) {
                // fall back to local store
            }
        }
        TicketState state = localStore.remove(qrId);
        return (state == null || state.isExpired()) ? null : state;
    }

    public QrCreateResult create() {
        String qrId = generateQrId();
        Instant expiresAt = Instant.now().plusSeconds(TTL_SECONDS);
        writeTicket(qrId, new TicketState("PENDING", null, expiresAt.toEpochMilli()));
        return new QrCreateResult(qrId, TTL_SECONDS, expiresAt.toString());
    }

    public void markScanned(long userId, String qrId) {
        TicketState current = readTicket(qrId);
        if (current == null) {
            throw BizException.notFound("登录二维码已过期或不存在");
        }
        if ("CONFIRMED".equals(current.status())) {
            throw BizException.conflict("该二维码已被使用，请刷新重试");
        }
        writeTicket(qrId, new TicketState("SCANNED", userId, current.expiresAtEpochMs()));
    }

    public void confirm(long userId, String qrId) {
        TicketState current = readTicket(qrId);
        if (current == null) {
            throw BizException.notFound("登录二维码已过期或不存在");
        }
        if ("CONFIRMED".equals(current.status())) {
            throw BizException.conflict("该二维码已被确认，请勿重复操作");
        }
        writeTicket(qrId, new TicketState("CONFIRMED", userId, current.expiresAtEpochMs()));
    }

    public QrPollResult pollStatus(String qrId, String userAgent, String ipAddress) {
        TicketState current = readTicket(qrId);
        if (current == null) {
            return new QrPollResult("EXPIRED", null, null, null, null);
        }
        if (!"CONFIRMED".equals(current.status()) || current.userId() == null) {
            return new QrPollResult(current.status(), null, null, null, null);
        }
        // 一次性消费票据，彻底防止重放攻击
        TicketState consumed = consumeTicket(qrId);
        if (consumed == null || !"CONFIRMED".equals(consumed.status()) || consumed.userId() == null) {
            return new QrPollResult("EXPIRED", null, null, null, null);
        }

        long userId = consumed.userId();
        var rows = db.queryForList(
                "SELECT u.display_name, u.status, COALESCE(s.session_version, 0) session_version "
                        + "FROM sys_user u LEFT JOIN auth_user_security s ON s.user_id = u.id WHERE u.id = ?",
                userId);
        if (rows.isEmpty()) {
            throw new BizException(HttpStatus.UNAUTHORIZED, 401, "账号不存在");
        }
        Map<String, Object> row = rows.get(0);
        int status = ((Number) row.get("status")).intValue();
        if (status != 1) {
            throw new BizException(HttpStatus.FORBIDDEN, 403, "账号已被禁用");
        }
        int sessionVersion = ((Number) row.get("session_version")).intValue();
        String displayName = String.valueOf(row.get("display_name"));

        String accessToken = jwtTokenProvider.createAccessToken(userId, sessionVersion);
        String refreshToken = webSessions.start(userId, userAgent, ipAddress, "WEB", "PC 扫码登录");
        return new QrPollResult(
                "CONFIRMED",
                accessToken,
                jwtTokenProvider.getAccessTokenTtlSeconds(),
                displayName,
                refreshToken);
    }
}
