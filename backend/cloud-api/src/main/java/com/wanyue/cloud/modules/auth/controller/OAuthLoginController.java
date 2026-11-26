package com.wanyue.cloud.modules.auth.controller;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.auth.service.WebSessionService;
import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import com.wanyue.cloud.modules.drive.AccountController;
import com.wanyue.cloud.modules.drive.storage.SecretCipher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 第三方 OAuth2 / OIDC 授权登录闭环（需求 21.2 / T157）：
 * 根据后台 sys_oauth_provider 动态配置发起授权跳转与回调登录，密钥全程密文存储、仅后端解密换票。
 */
@RestController
@RequestMapping("/api/v1/auth/oauth")
public class OAuthLoginController {
    private static final long STATE_TTL_MS = 5 * 60_000L;
    private static final tools.jackson.databind.json.JsonMapper JSON = new tools.jackson.databind.json.JsonMapper();

    private final JdbcTemplate db;
    private final SecretCipher cipher;
    private final JwtTokenProvider jwt;
    private final WebSessionService webSessions;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Map<String, StateEntry> states = new ConcurrentHashMap<>();

    private record StateEntry(String providerCode, long expiresAt) {}

    public OAuthLoginController(JdbcTemplate db, SecretCipher cipher, JwtTokenProvider jwt, WebSessionService webSessions) {
        this.db = db;
        this.cipher = cipher;
        this.jwt = jwt;
        this.webSessions = webSessions;
    }

    @GetMapping("/{code}/authorize")
    public void authorize(@PathVariable String code, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String normalized = code.strip().toLowerCase(Locale.ROOT);
        var provider = loadEnabledProvider(normalized);
        String authorizeUrl = String.valueOf(provider.get("authorize_url"));
        String clientId = String.valueOf(provider.get("client_id"));
        if (authorizeUrl.isBlank() || clientId.isBlank()) {
            throw new BizException(HttpStatus.BAD_REQUEST, 400, "该身份提供商尚未完成 Client ID 或授权地址配置");
        }
        String state = UUID.randomUUID().toString().replace("-", "");
        states.put(state, new StateEntry(normalized, System.currentTimeMillis() + STATE_TTL_MS));
        String callbackUrl = buildCallbackUrl(request, normalized);
        String sep = authorizeUrl.contains("?") ? "&" : "?";
        String target = authorizeUrl + sep
                + "response_type=code"
                + "&client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(callbackUrl)
                + "&state=" + encode(state)
                + "&scope=" + encode("github".equals(normalized) ? "read:user user:email" : "openid profile email");
        response.sendRedirect(target);
    }

    @GetMapping("/{code}/callback")
    @Transactional
    public void callback(
            @PathVariable String code,
            @RequestParam String state,
            @RequestParam(name = "code") String authCode,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        String normalized = code.strip().toLowerCase(Locale.ROOT);
        StateEntry entry = states.remove(state);
        if (entry == null || !entry.providerCode().equals(normalized) || entry.expiresAt() < System.currentTimeMillis()) {
            throw new BizException(HttpStatus.BAD_REQUEST, 400, "OAuth 授权状态无效或已过期，请重新登录");
        }
        var provider = loadEnabledProvider(normalized);
        String tokenUrl = String.valueOf(provider.get("token_url"));
        String clientId = String.valueOf(provider.get("client_id"));
        String clientSecret = cipher.decrypt(provider.get("client_secret_cipher") == null ? "" : String.valueOf(provider.get("client_secret_cipher")));
        String callbackUrl = buildCallbackUrl(request, normalized);

        String form = "grant_type=authorization_code"
                + "&client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&code=" + encode(authCode)
                + "&redirect_uri=" + encode(callbackUrl);

        String externalSubject;
        String displayName;
        try {
            HttpRequest tokenReq = HttpRequest.newBuilder(URI.create(tokenUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> tokenResp = httpClient.send(tokenReq, HttpResponse.BodyHandlers.ofString());
            if (tokenResp.statusCode() < 200 || tokenResp.statusCode() >= 300) {
                throw new BizException(HttpStatus.BAD_GATEWAY, 502, "第三方 OAuth 换取令牌失败");
            }
            var node = JSON.readTree(tokenResp.body());
            String providerAccessToken = node.has("access_token") ? node.get("access_token").asString() : "";
            if (providerAccessToken.isBlank()) {
                throw new BizException(HttpStatus.BAD_GATEWAY, 502, "第三方 OAuth 未返回有效访问令牌");
            }
            externalSubject = node.has("sub") ? node.get("sub").asString() : (normalized + ":" + Integer.toHexString(providerAccessToken.hashCode()));
            displayName = node.has("name") ? node.get("name").asString() : (normalized.toUpperCase(Locale.ROOT) + " 用户");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(HttpStatus.BAD_GATEWAY, 502, "第三方 OAuth 通信失败: " + e.getMessage());
        }

        long userId = findOrProvisionOAuthUser(normalized, externalSubject, displayName);
        String refresh = webSessions.start(userId);
        AccountController.cookie(response, refresh, request.isSecure());
        String accessToken = jwt.createAccessToken(userId, 0);
        response.sendRedirect("/app?oauth=ok#token=" + encode(accessToken));
    }

    private long findOrProvisionOAuthUser(String providerCode, String subject, String displayName) {
        var existing = db.queryForList(
                "SELECT user_id FROM auth_oauth_account WHERE provider=? AND provider_user_id=? AND deleted=0 LIMIT 1",
                providerCode, subject);
        if (!existing.isEmpty()) {
            return ((Number) existing.get(0).get("user_id")).longValue();
        }
        long uid = IdWorker.getId();
        db.update("INSERT INTO sys_user(id, display_name, status, deleted) VALUES (?, ?, 1, 0)", uid, displayName);
        db.update("INSERT INTO auth_user_security(id, user_id) VALUES (?, ?)", IdWorker.getId(), uid);
        db.update("INSERT INTO auth_oauth_account(id, user_id, provider, provider_user_id, deleted) VALUES (?, ?, ?, ?, 0)",
                IdWorker.getId(), uid, providerCode, subject);
        db.update("INSERT INTO space(id, owner_user_id, kind, name, description, icon, color, sort_order, created_by) "
                        + "VALUES (?, ?, 'PERSONAL', '默认空间', '默认空间', '', '#6366f1', 0, ?)",
                IdWorker.getId(), uid, uid);
        return uid;
    }

    private Map<String, Object> loadEnabledProvider(String code) {
        var rows = db.queryForList(
                "SELECT provider_code, display_name, enabled, client_id, client_secret_cipher, authorize_url, token_url "
                        + "FROM sys_oauth_provider WHERE provider_code=? AND enabled=1",
                code);
        if (rows.isEmpty()) {
            throw BizException.notFound("该第三方身份提供商未启用或不存在");
        }
        return rows.get(0);
    }

    private static String buildCallbackUrl(HttpServletRequest req, String code) {
        String proto = req.getHeader("X-Forwarded-Proto");
        String scheme = (proto != null && !proto.isBlank()) ? proto : req.getScheme();
        String host = req.getHeader("Host");
        if (host == null || host.isBlank()) {
            host = req.getServerName() + ":" + req.getServerPort();
        }
        return scheme + "://" + host + "/api/v1/auth/oauth/" + code + "/callback";
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
