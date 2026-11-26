package com.wanyue.cloud.modules.drive;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.modules.auth.service.WebSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class AccountController {
    private final WebSessionService sessions;
    private final JdbcTemplate db;
    private final PasswordEncoder passwordEncoder;
    private final DriveService driveService;
    private final com.wanyue.cloud.modules.ops.SystemPolicyService policyService;

    public AccountController(
            WebSessionService sessions,
            JdbcTemplate db,
            PasswordEncoder passwordEncoder,
            DriveService driveService,
            com.wanyue.cloud.modules.ops.SystemPolicyService policyService) {
        this.sessions = sessions;
        this.db = db;
        this.passwordEncoder = passwordEncoder;
        this.driveService = driveService;
        this.policyService = policyService;
    }

    public static void cookie(HttpServletResponse response, String token, boolean secure) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("cloudlane.refresh", token).httpOnly(true).secure(secure).sameSite("Strict")
                .path("/api/v1/auth").maxAge(token.isEmpty() ? Duration.ZERO : Duration.ofDays(30)).build().toString());
    }

    @PostMapping("/auth/refresh")
    public R<Map<String, Object>> refresh(
            @CookieValue(name = "cloudlane.refresh", required = false) String token,
            @RequestHeader(name = "X-Requested-With", required = false) String requestedWith,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (!"Cloudlane".equals(requestedWith)) throw new BizException(HttpStatus.FORBIDDEN, 403, "请求来源无效");
        var refreshed = sessions.refresh(token);
        cookie(response, refreshed.refreshToken(), request.isSecure());
        return R.ok(Map.of("accessToken", refreshed.accessToken(), "expiresIn", refreshed.expiresIn()));
    }

    @PostMapping("/auth/logout")
    public R<Void> logout(Authentication auth, HttpServletRequest request, HttpServletResponse response) {
        sessions.logout(DriveController.user(auth));
        cookie(response, "", request.isSecure());
        return R.ok();
    }

    @GetMapping("/auth/sessions")
    public R<List<WebSessionService.DeviceSession>> listSessions(
            Authentication auth,
            @CookieValue(name = "cloudlane.refresh", required = false) String token) {
        return R.ok(sessions.listActive(DriveController.user(auth), token));
    }

    @DeleteMapping("/auth/sessions/{id}")
    public R<Void> revokeSession(Authentication auth, @PathVariable long id) {
        sessions.revokeSession(DriveController.user(auth), id);
        return R.ok();
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me(Authentication auth) {
        long uid = DriveController.user(auth);
        var data = db.queryForMap("SELECT CAST(id AS CHAR) id,display_name displayName,avatar_url avatarUrl FROM sys_user WHERE id=? AND deleted=0", uid);
        data.put("admin", auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN")));
        data.put("usedBytes", driveService.usedBytes(uid));
        data.put("quotaBytes", policyService.userDefaultQuotaBytes());
        return R.ok(data);
    }

    public record ProfileInput(
            @NotBlank @Size(max = 64) String displayName,
            @Size(max = 512) String avatarUrl) {}

    @PutMapping("/me")
    public R<Void> updateMe(Authentication auth, @Valid @RequestBody ProfileInput input) {
        String normalizedAvatar = normalizeAvatarUrl(input.avatarUrl());
        if (input.avatarUrl() == null) {
            db.update("UPDATE sys_user SET display_name=?,updated_at=UTC_TIMESTAMP(3),version=version+1 WHERE id=? AND deleted=0",
                    input.displayName().strip(), DriveController.user(auth));
        } else {
            db.update("UPDATE sys_user SET display_name=?,avatar_url=?,updated_at=UTC_TIMESTAMP(3),version=version+1 WHERE id=? AND deleted=0",
                    input.displayName().strip(), normalizedAvatar, DriveController.user(auth));
        }
        return R.ok();
    }

    public record VerifyPasswordInput(@NotBlank @Size(max = 128) String password) {}

    @PostMapping("/me/verify-password")
    public R<Boolean> verifyPassword(Authentication auth, @Valid @RequestBody VerifyPasswordInput input) {
        var rows = db.queryForList(
                "SELECT password_hash FROM auth_password_credential WHERE user_id=? AND deleted=0 LIMIT 1",
                DriveController.user(auth));
        if (rows.isEmpty()) {
            throw new BizException(HttpStatus.BAD_REQUEST, 400, "当前账号未设置登录密码");
        }
        String hash = String.valueOf(rows.get(0).get("password_hash"));
        if (!passwordEncoder.matches(input.password(), hash)) {
            throw new BizException(HttpStatus.FORBIDDEN, 403, "密码错误，请重新输入");
        }
        return R.ok(true);
    }

    public record ChangePasswordInput(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 8, max = 32) String newPassword) {}

    @PutMapping("/me/password")
    public R<Void> changePassword(Authentication auth, @Valid @RequestBody ChangePasswordInput input) {
        long uid = DriveController.user(auth);
        var rows = db.queryForList(
                "SELECT id, password_hash FROM auth_password_credential WHERE user_id=? AND deleted=0 LIMIT 1", uid);
        if (rows.isEmpty()) {
            throw new BizException(HttpStatus.BAD_REQUEST, 400, "当前账号未设置登录密码");
        }
        String hash = String.valueOf(rows.get(0).get("password_hash"));
        if (!passwordEncoder.matches(input.currentPassword(), hash)) {
            throw new BizException(HttpStatus.FORBIDDEN, 403, "原密码错误");
        }
        long credId = ((Number) rows.get(0).get("id")).longValue();
        db.update("UPDATE auth_password_credential SET password_hash=?, updated_at=UTC_TIMESTAMP(3) WHERE id=?",
                passwordEncoder.encode(input.newPassword()), credId);
        db.update("UPDATE auth_user_security SET session_version=session_version+1 WHERE user_id=? AND deleted=0", uid);
        return R.ok();
    }

    private static String normalizeAvatarUrl(String raw) {
        if (raw == null) return null;
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) return null;
        String lower = trimmed.toLowerCase(Locale.ROOT);
        boolean safe = lower.startsWith("https://") || lower.startsWith("http://") || (trimmed.startsWith("/") && !trimmed.startsWith("//"));
        if (!safe) {
            throw new BizException(HttpStatus.BAD_REQUEST, 400, "头像地址仅支持 http(s) 或站内相对路径");
        }
        return trimmed;
    }
}
