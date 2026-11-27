package com.wanyue.cloud.modules.drive.storage;

import com.wanyue.cloud.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/**
 * 统一存储路由服务（需求 6.3 / 6.5 / 6.7）：
 * 根据 storage_profile_id 动态路由至 LOCAL（支持多独立根目录）、DB（数据库小文件存储）或 S3（AWS SigV4 兼容对象存储），
 * 实现默认存储实例热切换且历史对象按原 profile 正常读取。
 */
@Service
public class StorageRouterService {
    private final JdbcTemplate db;
    private final LocalStorageProvider defaultLocal;
    private final DatabaseStorageProvider dbProvider;
    private final S3StorageProvider s3Provider;
    private final SecretCipher cipher;
    private final Path materializeCacheDir;
    private final long cacheMaxBytes;

    public StorageRouterService(
            JdbcTemplate db,
            LocalStorageProvider defaultLocal,
            DatabaseStorageProvider dbProvider,
            S3StorageProvider s3Provider,
            SecretCipher cipher,
            @Value("${cloud.storage.worker-tmp:data/worker-tmp}") String workerTmp,
            @Value("${cloud.storage.cache-max-bytes:2147483648}") long cacheMaxBytes) throws IOException {
        this.db = db;
        this.defaultLocal = defaultLocal;
        this.dbProvider = dbProvider;
        this.s3Provider = s3Provider;
        this.cipher = cipher;
        this.materializeCacheDir = Path.of(workerTmp, "storage-cache").toAbsolutePath().normalize();
        this.cacheMaxBytes = Math.max(64L * 1024 * 1024, cacheMaxBytes);
        Files.createDirectories(this.materializeCacheDir);
    }

    public record StorageProfileMeta(
            long id, String provider, String rootPath,
            String endpoint, String bucket, String region,
            String accessKey, String secretCipher, Long maxFileBytes) {}

    public static final String USAGE_USER_FILES = "user_files";
    public static final String USAGE_SITE_ASSETS = "site_assets";
    public static final String USAGE_USER_AVATARS = "user_avatars";

    public long resolveDefaultProfileId() {
        var rows = db.queryForList("SELECT id FROM storage_profile WHERE is_default=TRUE ORDER BY id ASC LIMIT 1");
        if (!rows.isEmpty()) {
            return ((Number) rows.get(0).get("id")).longValue();
        }
        return 1L;
    }

    /** 按业务场景解析对应存储方案（user_files / site_assets / user_avatars），未单独指定时回退到系统默认存储实例。 */
    public long resolveProfileIdForUsage(String usage) {
        if (usage != null && !usage.isBlank()) {
            String key = "storage.route." + usage.strip().toLowerCase(Locale.ROOT);
            var rows = db.queryForList("SELECT policy_value FROM sys_policy WHERE policy_key=? LIMIT 1", key);
            if (!rows.isEmpty() && rows.get(0).get("policy_value") != null) {
                String raw = String.valueOf(rows.get(0).get("policy_value")).strip();
                if (!raw.isEmpty()) {
                    try {
                        long pid = Long.parseLong(raw);
                        Integer exists = db.queryForObject("SELECT COUNT(*) FROM storage_profile WHERE id=?", Integer.class, pid);
                        if (exists != null && exists > 0) {
                            return pid;
                        }
                    } catch (NumberFormatException ignored) { /* fallback to default */ }
                }
            }
        }
        return resolveDefaultProfileId();
    }

    public StorageProfileMeta getProfile(long profileId) {
        var rows = db.queryForList(
                "SELECT id, provider, root_path, endpoint, bucket, region, access_key, secret_cipher, max_file_bytes "
                        + "FROM storage_profile WHERE id=?",
                profileId);
        if (rows.isEmpty()) {
            return new StorageProfileMeta(1L, "LOCAL", "", null, null, null, null, null, null);
        }
        var row = rows.get(0);
        return new StorageProfileMeta(
                ((Number) row.get("id")).longValue(),
                String.valueOf(row.get("provider")),
                row.get("root_path") == null ? "" : String.valueOf(row.get("root_path")),
                row.get("endpoint") == null ? "" : String.valueOf(row.get("endpoint")),
                row.get("bucket") == null ? "" : String.valueOf(row.get("bucket")),
                row.get("region") == null ? "us-east-1" : String.valueOf(row.get("region")),
                row.get("access_key") == null ? "" : String.valueOf(row.get("access_key")),
                row.get("secret_cipher") == null ? "" : String.valueOf(row.get("secret_cipher")),
                row.get("max_file_bytes") == null ? null : ((Number) row.get("max_file_bytes")).longValue());
    }

    public StorageProvider.StoredObject write(long profileId, InputStream input, long maxBytes) throws IOException {
        StorageProfileMeta profile = getProfile(profileId);
        if ("DB".equalsIgnoreCase(profile.provider())) {
            long limit = profile.maxFileBytes() != null ? Math.min(maxBytes, profile.maxFileBytes()) : maxBytes;
            return dbProvider.put("db/" + UUID.randomUUID().toString().replace("-", ""), input, limit);
        }
        if ("S3".equalsIgnoreCase(profile.provider())) {
            String key = "objects/" + UUID.randomUUID().toString().replace("-", "");
            String secret = cipher.decrypt(profile.secretCipher());
            return s3Provider.putObject(profile.endpoint(), profile.bucket(), profile.region(),
                    profile.accessKey(), secret, key, input, maxBytes);
        }
        if (profile.id() == 1L || profile.rootPath().isBlank()) {
            return defaultLocal.put(UUID.randomUUID().toString(), input, maxBytes);
        }
        return writeLocalAtRoot(Path.of(profile.rootPath()).toAbsolutePath().normalize(), input, maxBytes);
    }

    public Path resolveReadablePath(long profileId, String storageKey) {
        StorageProfileMeta profile = getProfile(profileId);
        if ("DB".equalsIgnoreCase(profile.provider())) {
            return materializeDbObject(profileId, storageKey);
        }
        if ("S3".equalsIgnoreCase(profile.provider())) {
            return materializeS3Object(profile, storageKey);
        }
        if (profile.id() == 1L || profile.rootPath().isBlank()) {
            return defaultLocal.resolve(storageKey);
        }
        Path root = Path.of(profile.rootPath()).toAbsolutePath().normalize();
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new BizException(HttpStatus.BAD_REQUEST, 400, "非法存储对象路径");
        }
        return resolved;
    }

    public void delete(long profileId, String storageKey) throws IOException {
        StorageProfileMeta profile = getProfile(profileId);
        if ("DB".equalsIgnoreCase(profile.provider())) {
            dbProvider.delete(storageKey);
            Files.deleteIfExists(cacheFileFor(profileId, storageKey));
            return;
        }
        if ("S3".equalsIgnoreCase(profile.provider())) {
            String secret = cipher.decrypt(profile.secretCipher());
            s3Provider.deleteObject(profile.endpoint(), profile.bucket(), profile.region(),
                    profile.accessKey(), secret, storageKey);
            Files.deleteIfExists(cacheFileFor(profileId, storageKey));
            return;
        }
        if (profile.id() == 1L || profile.rootPath().isBlank()) {
            defaultLocal.delete(storageKey);
            return;
        }
        Path root = Path.of(profile.rootPath()).toAbsolutePath().normalize();
        Path resolved = root.resolve(storageKey).normalize();
        if (resolved.startsWith(root)) {
            Files.deleteIfExists(resolved);
        }
    }

    private Path cacheFileFor(long profileId, String storageKey) {
        String safeName = profileId + "_" + storageKey.replaceAll("[^a-zA-Z0-9._-]", "_");
        return materializeCacheDir.resolve(safeName + ".bin").normalize();
    }

    private void touchCache(Path cached) {
        try {
            Files.setLastModifiedTime(cached, FileTime.from(Instant.now()));
        } catch (IOException ignored) { /* best-effort LRU touch */ }
    }

    /** 服务端物化缓存超限时按最久未访问（LRU）清理至 80% 水位线，防止磁盘撑满 */
    private void evictCacheIfNeeded() {
        try (var stream = Files.list(materializeCacheDir)) {
            record Entry(Path path, long size, long lastModified) {}
            List<Entry> entries = new ArrayList<>();
            long total = 0L;
            for (Path p : stream.toList()) {
                if (!Files.isRegularFile(p) || !p.getFileName().toString().endsWith(".bin")) continue;
                try {
                    long sz = Files.size(p);
                    long lm = Files.getLastModifiedTime(p).toMillis();
                    entries.add(new Entry(p, sz, lm));
                    total += sz;
                } catch (IOException ignored) { /* file removed concurrently */ }
            }
            if (total <= cacheMaxBytes) return;
            long target = (long) (cacheMaxBytes * 0.8);
            entries.sort(Comparator.comparingLong(Entry::lastModified));
            for (Entry e : entries) {
                if (total <= target) break;
                try {
                    if (Files.deleteIfExists(e.path())) {
                        total -= e.size();
                    }
                } catch (IOException ignored) { /* ignore busy file */ }
            }
        } catch (IOException ignored) { /* non-blocking eviction */ }
    }

    private Path materializeDbObject(long profileId, String storageKey) {
        Path cached = cacheFileFor(profileId, storageKey);
        if (Files.isRegularFile(cached)) {
            touchCache(cached);
            return cached;
        }
        try (InputStream in = dbProvider.get(storageKey)) {
            Files.createDirectories(materializeCacheDir);
            Path tmp = Files.createTempFile(materializeCacheDir, "db-", ".tmp");
            try {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(tmp, cached, StandardCopyOption.REPLACE_EXISTING);
                touchCache(cached);
                evictCacheIfNeeded();
                return cached;
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw BizException.notFound("数据库存储对象读取失败");
        }
    }

    private Path materializeS3Object(StorageProfileMeta profile, String storageKey) {
        Path cached = cacheFileFor(profile.id(), storageKey);
        if (Files.isRegularFile(cached)) {
            touchCache(cached);
            return cached;
        }
        try {
            Files.createDirectories(materializeCacheDir);
            Path tmp = Files.createTempFile(materializeCacheDir, "s3-", ".tmp");
            try {
                String secret = cipher.decrypt(profile.secretCipher());
                s3Provider.downloadToPath(profile.endpoint(), profile.bucket(), profile.region(),
                        profile.accessKey(), secret, storageKey, tmp);
                Files.move(tmp, cached, StandardCopyOption.REPLACE_EXISTING);
                touchCache(cached);
                evictCacheIfNeeded();
                return cached;
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw BizException.notFound("S3 对象读取失败: " + e.getMessage());
        }
    }

    private StorageProvider.StoredObject writeLocalAtRoot(Path root, InputStream input, long maxBytes) throws IOException {
        Files.createDirectories(root);
        String key = UUID.randomUUID().toString();
        Path target = root.resolve(key).normalize();
        Path tmp = Files.createTempFile(root, ".put-", ".tmp");
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            long total = 0L;
            byte[] buf = new byte[65536];
            try (DigestInputStream din = new DigestInputStream(input, sha);
                 OutputStream out = Files.newOutputStream(tmp)) {
                int n;
                while ((n = din.read(buf)) >= 0) {
                    total += n;
                    if (total > maxBytes) {
                        throw new BizException(HttpStatus.PAYLOAD_TOO_LARGE, 413, "文件大小超出系统上传限制");
                    }
                    out.write(buf, 0, n);
                }
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return new StorageProvider.StoredObject(key, total, HexFormat.of().formatHex(sha.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
