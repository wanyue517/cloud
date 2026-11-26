package com.wanyue.cloud.modules.ops;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局动态策略配置服务：统一管理 sys_policy，替代所有业务硬编码常量，支持管理后台实时热生效。
 */
@Service
public class SystemPolicyService {
    private static final long CACHE_TTL_MS = 3000L;

    private final JdbcTemplate db;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile long lastLoadedAt = 0L;

    public SystemPolicyService(JdbcTemplate db) {
        this.db = db;
    }

    public void invalidateCache() {
        lastLoadedAt = 0L;
        cache.clear();
    }

    private void ensureFresh() {
        long now = System.currentTimeMillis();
        if (now - lastLoadedAt < CACHE_TTL_MS && !cache.isEmpty()) {
            return;
        }
        try {
            var rows = db.queryForList("SELECT policy_key, policy_value FROM sys_policy");
            Map<String, String> fresh = new HashMap<>();
            for (var r : rows) {
                if (r.get("policy_key") != null) {
                    fresh.put(String.valueOf(r.get("policy_key")),
                            r.get("policy_value") == null ? "" : String.valueOf(r.get("policy_value")));
                }
            }
            cache.clear();
            cache.putAll(fresh);
            lastLoadedAt = now;
        } catch (Exception ignored) {
        }
    }

    public String getString(String key, String defaultVal) {
        ensureFresh();
        String val = cache.get(key);
        return val == null ? defaultVal : val;
    }

    public boolean getBool(String key, boolean defaultVal) {
        String val = getString(key, null);
        if (val == null || val.isBlank()) return defaultVal;
        return Boolean.parseBoolean(val.trim());
    }

    public int getInt(String key, int defaultVal, int min, int max) {
        String val = getString(key, null);
        if (val == null || val.isBlank()) return defaultVal;
        try {
            int parsed = Integer.parseInt(val.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public long getLong(String key, long defaultVal, long min, long max) {
        String val = getString(key, null);
        if (val == null || val.isBlank()) return defaultVal;
        try {
            long parsed = Long.parseLong(val.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public Set<String> getBlockedExtensions() {
        String raw = getString("blocked_extensions", "");
        if (raw.isBlank()) return Set.of();
        Set<String> set = new LinkedHashSet<>();
        for (String part : raw.split("[,;\\s]+")) {
            String clean = part.trim().toLowerCase(Locale.ROOT).replaceFirst("^\\.+", "");
            if (!clean.isEmpty()) set.add(clean);
        }
        return set;
    }

    public void put(String key, String value) {
        String safe = value == null ? "" : value;
        db.update(
                "INSERT INTO sys_policy(policy_key, policy_value) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE policy_value=VALUES(policy_value), updated_at=UTC_TIMESTAMP(3)",
                key, safe);
        cache.put(key, safe);
        lastLoadedAt = System.currentTimeMillis();
    }

    // 便捷业务策略读取
    public int maxFileVersions() { return getInt("max_file_versions", 20, 1, 200); }
    public int uploadChunkSizeMb() { return getInt("upload_chunk_size_mb", 4, 1, 64); }
    public int uploadChunkSizeBytes() { return uploadChunkSizeMb() * 1024 * 1024; }
    public long uploadMaxFileMb() { return getLong("upload_max_file_mb", 10240L, 1L, 102400L); }
    public long uploadMaxFileBytes() { return uploadMaxFileMb() * 1024L * 1024L; }
    public int uploadMaxConcurrentSessions() { return getInt("upload_max_concurrent_sessions", 20, 1, 100); }
    public int uploadSessionTtlHours() { return getInt("upload_session_ttl_hours", 24, 1, 168); }
    public long userDefaultQuotaGb() { return getLong("user_default_quota_gb", 100L, 0L, 102400L); }
    public long userDefaultQuotaBytes() { return userDefaultQuotaGb() * 1024L * 1024L * 1024L; }
    public int maxDirectoryDepth() { return getInt("max_directory_depth", 256, 16, 512); }
    public int trashRetentionDays() { return getInt("trash_retention_days", 30, 1, 365); }
    public boolean allowRegister() { return getBool("auth_allow_register", true); }
    public boolean requireEmailVerify() { return getBool("auth_require_email_verify", true); }
    public boolean allowPasswordLogin() { return getBool("auth_allow_password_login", true); }
    public int maxLoginFailures() { return getInt("auth_max_login_failures", 5, 3, 20); }
    public int loginLockMinutes() { return getInt("auth_lock_minutes", 15, 1, 1440); }
}
