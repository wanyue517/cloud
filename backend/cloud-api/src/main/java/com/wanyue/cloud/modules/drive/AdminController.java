package com.wanyue.cloud.modules.drive;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.modules.drive.storage.DatabaseStorageProvider;
import com.wanyue.cloud.modules.drive.storage.S3StorageProvider;
import com.wanyue.cloud.modules.drive.storage.SecretCipher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {
    private final JdbcTemplate db;
    private final UploadService uploadService;
    private final SecretCipher cipher;
    private final S3StorageProvider s3Provider;
    private final DatabaseStorageProvider dbProvider;
    private final com.wanyue.cloud.modules.ops.SystemPolicyService policyService;

    public AdminController(
            JdbcTemplate db,
            UploadService uploadService,
            SecretCipher cipher,
            S3StorageProvider s3Provider,
            DatabaseStorageProvider dbProvider,
            com.wanyue.cloud.modules.ops.SystemPolicyService policyService) {
        this.db = db;
        this.uploadService = uploadService;
        this.cipher = cipher;
        this.s3Provider = s3Provider;
        this.dbProvider = dbProvider;
        this.policyService = policyService;
    }

    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        return R.ok(Map.of(
                "users", db.queryForObject("SELECT COUNT(*) FROM sys_user WHERE deleted=0", Long.class),
                "spaces", db.queryForObject("SELECT COUNT(*) FROM space WHERE deleted=0", Long.class),
                "files", db.queryForObject("SELECT COUNT(*) FROM file_node WHERE node_type='FILE' AND deleted_at IS NULL", Long.class),
                "bytes", db.queryForObject("SELECT COALESCE(SUM(size_bytes),0) FROM blob_object", Long.class)));
    }

    @GetMapping("/users")
    public R<List<Map<String, Object>>> users(@RequestParam(defaultValue = "1") int page) {
        int safe = Math.max(1, page);
        return R.ok(db.queryForList(
                "SELECT CAST(id AS CHAR) id,display_name displayName,status,registered_at registeredAt FROM sys_user WHERE deleted=0 ORDER BY registered_at,id LIMIT 100 OFFSET ?",
                (long) (safe - 1) * 100));
    }

    public record Status(@Min(0) @Max(2) int status) {}

    @PutMapping("/users/{id}/status")
    @Transactional
    public R<Void> status(Authentication auth, @PathVariable long id, @Valid @RequestBody Status input) {
        if (id == DriveController.user(auth)) throw BizException.conflict("不能禁用当前账号");
        if (db.queryForObject("SELECT COUNT(*) FROM auth_user_role ur JOIN auth_role r ON r.id=ur.role_id WHERE ur.user_id=? AND ur.deleted=0 AND r.role_code='SUPER_ADMIN'", Long.class, id) > 0)
            throw BizException.conflict("超级管理员状态不能在此修改");
        if (db.update("UPDATE sys_user SET status=? WHERE id=? AND deleted=0", input.status(), id) != 1)
            throw BizException.notFound("用户不存在");
        db.update("UPDATE auth_user_security SET session_version=session_version+1 WHERE user_id=? AND deleted=0", id);
        return R.ok();
    }

    public record StorageProfileInput(
            @NotBlank @Size(max = 100) String name,
            @Pattern(regexp = "LOCAL|S3|DB") String provider,
            @Size(max = 1024) String rootPath,
            @Size(max = 512) String endpoint,
            @Size(max = 128) String bucket,
            @Size(max = 64) String region,
            @Size(max = 255) String accessKey,
            @Size(max = 255) String secretKey,
            Long maxFileBytes) {}

    @GetMapping("/storage-profiles")
    public R<List<Map<String, Object>>> storageProfiles() {
        var rows = db.queryForList(
                "SELECT CAST(p.id AS CHAR) id, p.name, p.provider, p.root_path rootPath, p.endpoint, p.bucket, p.region, "
                        + "p.access_key accessKey, (p.secret_cipher IS NOT NULL AND p.secret_cipher <> '') hasSecret, "
                        + "p.max_file_bytes maxFileBytes, p.is_default isDefault, "
                        + "COUNT(b.id) objectCount, COALESCE(SUM(b.size_bytes), 0) totalBytes "
                        + "FROM storage_profile p LEFT JOIN blob_object b ON b.storage_profile_id = p.id "
                        + "GROUP BY p.id, p.name, p.provider, p.root_path, p.endpoint, p.bucket, p.region, p.access_key, p.secret_cipher, p.max_file_bytes, p.is_default "
                        + "ORDER BY p.is_default DESC, p.id ASC");
        for (var row : rows) {
            row.put("hasSecret", toBool(row.get("hasSecret")));
            row.put("isDefault", toBool(row.get("isDefault")));
        }
        return R.ok(rows);
    }

    private static boolean toBool(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    @PostMapping("/storage-profiles")
    @Transactional
    public R<String> createStorageProfile(@Valid @RequestBody StorageProfileInput input) {
        String provider = input.provider();
        String root = input.rootPath() == null ? "" : input.rootPath().strip();
        if ("LOCAL".equals(provider)) {
            if (root.isEmpty() || root.contains("..")) {
                throw new BizException(HttpStatus.BAD_REQUEST, 400, "本地存储路径不能为空且不允许包含相对上级引用");
            }
        } else if ("S3".equals(provider)) {
            if (input.endpoint() == null || input.endpoint().isBlank() || input.bucket() == null || input.bucket().isBlank()) {
                throw new BizException(HttpStatus.BAD_REQUEST, 400, "S3 存储必须提供 Endpoint 和 Bucket");
            }
            if (root.isEmpty()) root = "s3://" + input.bucket().strip();
        } else {
            if (root.isEmpty()) root = "db://blob_db_content";
        }
        String encryptedSecret = (input.secretKey() == null || input.secretKey().isBlank()) ? null : cipher.encrypt(input.secretKey().strip());
        long id = IdWorker.getId();
        db.update(
                "INSERT INTO storage_profile(id, name, provider, root_path, endpoint, bucket, region, access_key, secret_cipher, max_file_bytes, is_default) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, FALSE)",
                id, input.name().strip(), provider, root,
                input.endpoint() == null ? null : input.endpoint().strip(),
                input.bucket() == null ? null : input.bucket().strip(),
                input.region() == null ? null : input.region().strip(),
                input.accessKey() == null ? null : input.accessKey().strip(),
                encryptedSecret,
                input.maxFileBytes());
        return R.ok(String.valueOf(id));
    }

    @PutMapping("/storage-profiles/{id}")
    @Transactional
    public R<Void> updateStorageProfile(@PathVariable long id, @Valid @RequestBody StorageProfileInput input) {
        var existing = db.queryForList("SELECT id FROM storage_profile WHERE id=?", id);
        if (existing.isEmpty()) throw BizException.notFound("存储实例不存在");
        String provider = input.provider();
        String root = input.rootPath() == null ? "" : input.rootPath().strip();
        if ("LOCAL".equals(provider)) {
            if (root.isEmpty() || root.contains("..")) {
                throw new BizException(HttpStatus.BAD_REQUEST, 400, "本地存储路径不能为空且不允许包含相对上级引用");
            }
        } else if ("S3".equals(provider)) {
            if (input.endpoint() == null || input.endpoint().isBlank() || input.bucket() == null || input.bucket().isBlank()) {
                throw new BizException(HttpStatus.BAD_REQUEST, 400, "S3 存储必须提供 Endpoint 和 Bucket");
            }
            if (root.isEmpty()) root = "s3://" + input.bucket().strip();
        } else {
            if (root.isEmpty()) root = "db://blob_db_content";
        }
        db.update(
                "UPDATE storage_profile SET name=?, provider=?, root_path=?, endpoint=?, bucket=?, region=?, access_key=?, max_file_bytes=? WHERE id=?",
                input.name().strip(), provider, root,
                input.endpoint() == null ? null : input.endpoint().strip(),
                input.bucket() == null ? null : input.bucket().strip(),
                input.region() == null ? null : input.region().strip(),
                input.accessKey() == null ? null : input.accessKey().strip(),
                input.maxFileBytes(), id);
        if (input.secretKey() != null && !input.secretKey().isBlank()) {
            db.update("UPDATE storage_profile SET secret_cipher=? WHERE id=?",
                    cipher.encrypt(input.secretKey().strip()), id);
        }
        return R.ok();
    }

    public record StorageProfileTestInput(
            Long id,
            @Pattern(regexp = "LOCAL|S3|DB") String provider,
            @Size(max = 1024) String rootPath,
            @Size(max = 512) String endpoint,
            @Size(max = 128) String bucket,
            @Size(max = 64) String region,
            @Size(max = 255) String accessKey,
            @Size(max = 255) String secretKey) {}

    /** 表单即时测试：无需先入库即可探测 LOCAL / S3 / DB 连通性；编辑场景若未填 secretKey 自动复用库内密文 */
    @PostMapping("/storage-profiles/test-config")
    public R<Map<String, Object>> testStorageProfileConfig(@Valid @RequestBody StorageProfileTestInput input) {
        String provider = input.provider();
        if ("S3".equalsIgnoreCase(provider)) {
            String secret = input.secretKey() == null ? "" : input.secretKey().strip();
            if (secret.isEmpty() && input.id() != null) {
                var rows = db.queryForList("SELECT secret_cipher FROM storage_profile WHERE id=?", input.id());
                if (!rows.isEmpty() && rows.get(0).get("secret_cipher") != null) {
                    secret = cipher.decrypt(String.valueOf(rows.get(0).get("secret_cipher")));
                }
            }
            return R.ok(s3Provider.testConnection(
                    input.endpoint() == null ? "" : input.endpoint().strip(),
                    input.bucket() == null ? "" : input.bucket().strip(),
                    input.region() == null || input.region().isBlank() ? "us-east-1" : input.region().strip(),
                    input.accessKey() == null ? "" : input.accessKey().strip(),
                    secret));
        }
        if ("DB".equalsIgnoreCase(provider)) {
            long start = System.currentTimeMillis();
            try {
                var stored = dbProvider.put("probe/" + IdWorker.getId(), new ByteArrayInputStream("probe".getBytes(StandardCharsets.UTF_8)), 1024);
                boolean ok = dbProvider.exists(stored.key());
                dbProvider.delete(stored.key());
                return R.ok(Map.of("ok", ok, "provider", "DB", "elapsedMs", System.currentTimeMillis() - start));
            } catch (IOException e) {
                throw new BizException(HttpStatus.SERVICE_UNAVAILABLE, 16003, "数据库存储探针失败: " + e.getMessage());
            }
        }
        String root = input.rootPath() == null ? "" : input.rootPath().strip();
        if (root.isEmpty() || root.contains("..")) {
            throw new BizException(HttpStatus.BAD_REQUEST, 400, "本地存储路径不能为空且不允许包含相对上级引用");
        }
        long start = System.currentTimeMillis();
        Path dir = Path.of(root).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            Path probe = Files.createTempFile(dir, ".storage-probe-", ".tmp");
            Files.writeString(probe, "ok");
            Files.deleteIfExists(probe);
            return R.ok(Map.of("ok", true, "path", dir.toString(), "elapsedMs", System.currentTimeMillis() - start));
        } catch (IOException e) {
            throw new BizException(HttpStatus.SERVICE_UNAVAILABLE, 16003, "本地存储目录不可写: " + e.getMessage());
        }
    }

    @PutMapping("/storage-profiles/{id}/default")
    @Transactional
    public R<Void> setDefaultStorageProfile(@PathVariable long id) {
        Integer exists = db.queryForObject("SELECT COUNT(*) FROM storage_profile WHERE id=?", Integer.class, id);
        if (exists == null || exists == 0) throw BizException.notFound("存储实例不存在");
        db.update("UPDATE storage_profile SET is_default=FALSE WHERE is_default=TRUE");
        db.update("UPDATE storage_profile SET is_default=TRUE WHERE id=?", id);
        return R.ok();
    }

    @PostMapping("/storage-profiles/{id}/test")
    public R<Map<String, Object>> testStorageProfile(@PathVariable long id) {
        var rows = db.queryForList("SELECT provider, root_path, endpoint, bucket, region, access_key, secret_cipher FROM storage_profile WHERE id=?", id);
        if (rows.isEmpty()) throw BizException.notFound("存储实例不存在");
        var row = rows.get(0);
        String provider = String.valueOf(row.get("provider"));
        if ("S3".equalsIgnoreCase(provider)) {
            String secret = cipher.decrypt(row.get("secret_cipher") == null ? "" : String.valueOf(row.get("secret_cipher")));
            return R.ok(s3Provider.testConnection(
                    row.get("endpoint") == null ? "" : String.valueOf(row.get("endpoint")),
                    row.get("bucket") == null ? "" : String.valueOf(row.get("bucket")),
                    row.get("region") == null ? "us-east-1" : String.valueOf(row.get("region")),
                    row.get("access_key") == null ? "" : String.valueOf(row.get("access_key")),
                    secret));
        }
        if ("DB".equalsIgnoreCase(provider)) {
            long start = System.currentTimeMillis();
            try {
                var stored = dbProvider.put("probe/" + IdWorker.getId(), new ByteArrayInputStream("probe".getBytes(StandardCharsets.UTF_8)), 1024);
                boolean ok = dbProvider.exists(stored.key());
                dbProvider.delete(stored.key());
                return R.ok(Map.of("ok", ok, "provider", "DB", "elapsedMs", System.currentTimeMillis() - start));
            } catch (IOException e) {
                throw new BizException(HttpStatus.SERVICE_UNAVAILABLE, 16003, "数据库存储探针失败: " + e.getMessage());
            }
        }
        String rootPath = String.valueOf(row.get("root_path"));
        long start = System.currentTimeMillis();
        Path dir = Path.of(rootPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            Path probe = Files.createTempFile(dir, ".storage-probe-", ".tmp");
            Files.writeString(probe, "ok");
            Files.deleteIfExists(probe);
            long elapsed = System.currentTimeMillis() - start;
            return R.ok(Map.of("ok", true, "path", dir.toString(), "elapsedMs", elapsed));
        } catch (IOException e) {
            throw new BizException(HttpStatus.SERVICE_UNAVAILABLE, 16003, "本地存储目录不可写: " + e.getMessage());
        }
    }

    @GetMapping("/tasks")
    public R<Map<String, Object>> tasks(@RequestParam(required = false) String status) {
        var summaryRows = db.queryForList("SELECT status, COUNT(*) cnt FROM upload_session GROUP BY status");
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("UPLOADING", 0L);
        counts.put("COMPLETED", 0L);
        counts.put("CANCELLED", 0L);
        counts.put("EXPIRED", 0L);
        for (var row : summaryRows) {
            counts.put(String.valueOf(row.get("status")), ((Number) row.get("cnt")).longValue());
        }
        String sql = "SELECT CAST(u.id AS CHAR) id, u.name, u.size_bytes sizeBytes, u.chunk_count chunkCount, u.status, "
                + "u.created_at createdAt, u.expires_at expiresAt, COALESCE(usr.display_name, '') ownerName, COALESCE(sp.name, '') spaceName "
                + "FROM upload_session u "
                + "LEFT JOIN sys_user usr ON usr.id = u.user_id "
                + "LEFT JOIN space sp ON sp.id = u.space_id ";
        List<Map<String, Object>> items;
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            items = db.queryForList(sql + "WHERE u.status=? ORDER BY u.created_at DESC LIMIT 100", status.trim().toUpperCase(Locale.ROOT));
        } else {
            items = db.queryForList(sql + "ORDER BY u.created_at DESC LIMIT 100");
        }
        return R.ok(Map.of("summary", counts, "items", items));
    }

    @PostMapping("/tasks/cleanup-expired")
    public R<Map<String, Object>> cleanupExpiredTasks() {
        int cleaned = uploadService.cleanExpiredAndCount();
        return R.ok(Map.of("cleaned", cleaned));
    }

    public record FilePolicyInput(
            @Min(1) @Max(200) int maxFileVersions,
            @Min(1) @Max(64) Integer uploadChunkSizeMb,
            @Min(1) @Max(102400) Long uploadMaxFileMb,
            @Min(1) @Max(100) Integer uploadMaxConcurrentSessions,
            @Min(1) @Max(168) Integer uploadSessionTtlHours,
            @Min(0) @Max(102400) Long userDefaultQuotaGb,
            @Min(16) @Max(512) Integer maxDirectoryDepth,
            @Min(1) @Max(365) Integer trashRetentionDays,
            @Size(max = 500) String blockedExtensions) {}

    @GetMapping("/settings/files")
    public R<Map<String, Object>> filePolicy() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("maxFileVersions", policyService.maxFileVersions());
        data.put("uploadChunkSizeMb", policyService.uploadChunkSizeMb());
        data.put("uploadMaxFileMb", policyService.uploadMaxFileMb());
        data.put("uploadMaxConcurrentSessions", policyService.uploadMaxConcurrentSessions());
        data.put("uploadSessionTtlHours", policyService.uploadSessionTtlHours());
        data.put("userDefaultQuotaGb", policyService.userDefaultQuotaGb());
        data.put("maxDirectoryDepth", policyService.maxDirectoryDepth());
        data.put("trashRetentionDays", policyService.trashRetentionDays());
        data.put("blockedExtensions", policyService.getString("blocked_extensions", ""));
        return R.ok(data);
    }

    @PutMapping("/settings/files")
    @Transactional
    public R<Map<String, Object>> updateFilePolicy(@Valid @RequestBody FilePolicyInput input) {
        policyService.put("max_file_versions", String.valueOf(input.maxFileVersions()));
        if (input.uploadChunkSizeMb() != null) policyService.put("upload_chunk_size_mb", String.valueOf(input.uploadChunkSizeMb()));
        if (input.uploadMaxFileMb() != null) policyService.put("upload_max_file_mb", String.valueOf(input.uploadMaxFileMb()));
        if (input.uploadMaxConcurrentSessions() != null) policyService.put("upload_max_concurrent_sessions", String.valueOf(input.uploadMaxConcurrentSessions()));
        if (input.uploadSessionTtlHours() != null) policyService.put("upload_session_ttl_hours", String.valueOf(input.uploadSessionTtlHours()));
        if (input.userDefaultQuotaGb() != null) policyService.put("user_default_quota_gb", String.valueOf(input.userDefaultQuotaGb()));
        if (input.maxDirectoryDepth() != null) policyService.put("max_directory_depth", String.valueOf(input.maxDirectoryDepth()));
        if (input.trashRetentionDays() != null) policyService.put("trash_retention_days", String.valueOf(input.trashRetentionDays()));
        if (input.blockedExtensions() != null) policyService.put("blocked_extensions", input.blockedExtensions().strip());
        db.update("UPDATE user_version_policy SET max_versions=? WHERE max_versions > ?",
                input.maxFileVersions(), input.maxFileVersions());
        policyService.invalidateCache();
        return filePolicy();
    }

    private String readPolicy(String key, String def) {
        var rows = db.queryForList("SELECT policy_value FROM sys_policy WHERE policy_key=?", key);
        return rows.isEmpty() || rows.get(0).get("policy_value") == null ? def : String.valueOf(rows.get(0).get("policy_value"));
    }

    private void upsertPolicy(String key, String value) {
        policyService.put(key, value);
    }

    public record SiteSettingsInput(
            @NotBlank @Size(max = 80) String siteName,
            @Size(max = 512) String siteLogoUrl,
            @Size(max = 1000) String siteAnnouncement) {}

    @GetMapping("/settings/site")
    public R<Map<String, Object>> siteSettings() {
        return R.ok(Map.of(
                "siteName", readPolicy("site_name", "Cloudlane"),
                "siteLogoUrl", readPolicy("site_logo_url", ""),
                "siteAnnouncement", readPolicy("site_announcement", "")));
    }

    @PutMapping("/settings/site")
    @Transactional
    public R<Map<String, Object>> updateSiteSettings(@Valid @RequestBody SiteSettingsInput input) {
        upsertPolicy("site_name", input.siteName().strip());
        upsertPolicy("site_logo_url", input.siteLogoUrl() == null ? "" : input.siteLogoUrl().strip());
        upsertPolicy("site_announcement", input.siteAnnouncement() == null ? "" : input.siteAnnouncement().strip());
        return siteSettings();
    }

    public record AuthAndSmtpInput(
            boolean allowRegister,
            boolean requireEmailVerify,
            boolean allowPasswordLogin,
            @Size(max = 255) String smtpHost,
            @Min(1) @Max(65535) int smtpPort,
            @Size(max = 255) String smtpUsername,
            @Size(max = 255) String smtpFrom,
            @Size(max = 255) String smtpPassword) {}

    @GetMapping("/settings/auth")
    public R<Map<String, Object>> authSettings() {
        var oauth = db.queryForList(
                "SELECT provider_code providerCode, display_name displayName, enabled, client_id clientId, "
                        + "(client_secret_cipher IS NOT NULL AND client_secret_cipher <> '') hasSecret, "
                        + "authorize_url authorizeUrl, token_url tokenUrl FROM sys_oauth_provider ORDER BY provider_code");
        for (var row : oauth) {
            row.put("enabled", toBool(row.get("enabled")));
            row.put("hasSecret", toBool(row.get("hasSecret")));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("allowRegister", Boolean.parseBoolean(readPolicy("auth_allow_register", "true")));
        data.put("requireEmailVerify", Boolean.parseBoolean(readPolicy("auth_require_email_verify", "true")));
        data.put("allowPasswordLogin", Boolean.parseBoolean(readPolicy("auth_allow_password_login", "true")));
        data.put("smtpHost", readPolicy("smtp_host", ""));
        data.put("smtpPort", Integer.parseInt(readPolicy("smtp_port", "587")));
        data.put("smtpUsername", readPolicy("smtp_username", ""));
        data.put("smtpFrom", readPolicy("smtp_from", ""));
        data.put("hasSmtpPassword", !readPolicy("smtp_password_cipher", "").isEmpty());
        data.put("oauthProviders", oauth);
        return R.ok(data);
    }

    @PutMapping("/settings/auth")
    @Transactional
    public R<Map<String, Object>> updateAuthSettings(@Valid @RequestBody AuthAndSmtpInput input) {
        upsertPolicy("auth_allow_register", String.valueOf(input.allowRegister()));
        upsertPolicy("auth_require_email_verify", String.valueOf(input.requireEmailVerify()));
        upsertPolicy("auth_allow_password_login", String.valueOf(input.allowPasswordLogin()));
        upsertPolicy("smtp_host", input.smtpHost() == null ? "" : input.smtpHost().strip());
        upsertPolicy("smtp_port", String.valueOf(input.smtpPort()));
        upsertPolicy("smtp_username", input.smtpUsername() == null ? "" : input.smtpUsername().strip());
        upsertPolicy("smtp_from", input.smtpFrom() == null ? "" : input.smtpFrom().strip());
        if (input.smtpPassword() != null && !input.smtpPassword().isBlank()) {
            upsertPolicy("smtp_password_cipher", cipher.encrypt(input.smtpPassword().strip()));
        }
        return authSettings();
    }

    public record OAuthProviderInput(
            boolean enabled,
            @Size(max = 255) String clientId,
            @Size(max = 255) String clientSecret,
            @Size(max = 512) String authorizeUrl,
            @Size(max = 512) String tokenUrl) {}

    @PutMapping("/oauth-providers/{code}")
    @Transactional
    public R<Void> updateOAuthProvider(@PathVariable String code, @Valid @RequestBody OAuthProviderInput input) {
        int updated = db.update(
                "UPDATE sys_oauth_provider SET enabled=?, client_id=?, authorize_url=?, token_url=?, updated_at=UTC_TIMESTAMP(3) WHERE provider_code=?",
                input.enabled() ? 1 : 0,
                input.clientId() == null ? "" : input.clientId().strip(),
                input.authorizeUrl() == null ? "" : input.authorizeUrl().strip(),
                input.tokenUrl() == null ? "" : input.tokenUrl().strip(),
                code.strip().toLowerCase(Locale.ROOT));
        if (updated != 1) throw BizException.notFound("身份提供商不存在");
        if (input.clientSecret() != null && !input.clientSecret().isBlank()) {
            db.update("UPDATE sys_oauth_provider SET client_secret_cipher=? WHERE provider_code=?",
                    cipher.encrypt(input.clientSecret().strip()), code.strip().toLowerCase(Locale.ROOT));
        }
        return R.ok();
    }
}
