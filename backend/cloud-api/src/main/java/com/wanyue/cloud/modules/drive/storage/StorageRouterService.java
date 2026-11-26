package com.wanyue.cloud.modules.drive.storage;

import com.wanyue.cloud.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * 统一存储路由服务（需求 6.3 / 6.5）：
 * 根据 storage_profile_id 动态路由至 LOCAL（支持多独立根目录）、DB（数据库小文件存储）或 S3，
 * 实现默认存储实例热切换且历史对象按原 profile 正常读取。
 */
@Service
public class StorageRouterService {
    private final JdbcTemplate db;
    private final LocalStorageProvider defaultLocal;
    private final DatabaseStorageProvider dbProvider;
    private final Path materializeCacheDir;

    public StorageRouterService(
            JdbcTemplate db,
            LocalStorageProvider defaultLocal,
            DatabaseStorageProvider dbProvider,
            @Value("${cloud.storage.worker-tmp:data/worker-tmp}") String workerTmp) throws IOException {
        this.db = db;
        this.defaultLocal = defaultLocal;
        this.dbProvider = dbProvider;
        this.materializeCacheDir = Path.of(workerTmp, "storage-cache").toAbsolutePath().normalize();
        Files.createDirectories(this.materializeCacheDir);
    }

    public record StorageProfileMeta(long id, String provider, String rootPath, Long maxFileBytes) {}

    public long resolveDefaultProfileId() {
        var rows = db.queryForList("SELECT id FROM storage_profile WHERE is_default=TRUE ORDER BY id ASC LIMIT 1");
        if (!rows.isEmpty()) {
            return ((Number) rows.get(0).get("id")).longValue();
        }
        return 1L;
    }

    public StorageProfileMeta getProfile(long profileId) {
        var rows = db.queryForList(
                "SELECT id, provider, root_path, max_file_bytes FROM storage_profile WHERE id=?", profileId);
        if (rows.isEmpty()) {
            return new StorageProfileMeta(1L, "LOCAL", "", null);
        }
        var row = rows.get(0);
        return new StorageProfileMeta(
                ((Number) row.get("id")).longValue(),
                String.valueOf(row.get("provider")),
                row.get("root_path") == null ? "" : String.valueOf(row.get("root_path")),
                row.get("max_file_bytes") == null ? null : ((Number) row.get("max_file_bytes")).longValue());
    }

    public StorageProvider.StoredObject write(long profileId, InputStream input, long maxBytes) throws IOException {
        StorageProfileMeta profile = getProfile(profileId);
        if ("DB".equalsIgnoreCase(profile.provider())) {
            long limit = profile.maxFileBytes() != null ? Math.min(maxBytes, profile.maxFileBytes()) : maxBytes;
            return dbProvider.put("db/" + UUID.randomUUID().toString().replace("-", ""), input, limit);
        }
        if ("S3".equalsIgnoreCase(profile.provider())) {
            throw new BizException(HttpStatus.NOT_IMPLEMENTED, 501,
                    "当前 S3 实例仅支持连接测试，对象直写请配置完整对象存储凭证后启用");
        }
        // LOCAL 存储：按 profile 专属 root_path 写入，避免写死单一目录
        if (profile.id() == 1L || profile.rootPath().isBlank()) {
            return defaultLocal.put(UUID.randomUUID().toString(), input, maxBytes);
        }
        return writeLocalAtRoot(Path.of(profile.rootPath()).toAbsolutePath().normalize(), input, maxBytes);
    }

    public Path resolveReadablePath(long profileId, String storageKey) {
        StorageProfileMeta profile = getProfile(profileId);
        if ("DB".equalsIgnoreCase(profile.provider())) {
            return materializeDbObject(storageKey);
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
            Path cached = cacheFileFor(storageKey);
            Files.deleteIfExists(cached);
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

    private Path cacheFileFor(String storageKey) {
        String safeName = storageKey.replaceAll("[^a-zA-Z0-9._-]", "_");
        return materializeCacheDir.resolve(safeName + ".bin").normalize();
    }

    private Path materializeDbObject(String storageKey) {
        Path cached = cacheFileFor(storageKey);
        if (Files.isRegularFile(cached)) {
            return cached;
        }
        try (InputStream in = dbProvider.get(storageKey)) {
            Files.createDirectories(materializeCacheDir);
            Path tmp = Files.createTempFile(materializeCacheDir, "db-", ".tmp");
            try {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(tmp, cached, StandardCopyOption.REPLACE_EXISTING);
                return cached;
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw BizException.notFound("数据库存储对象读取失败");
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
