package com.wanyue.cloud.modules.drive.storage;

import com.wanyue.cloud.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class DatabaseStorageProvider implements StorageProvider {
    private final JdbcTemplate db;
    private final long maxFileBytes;

    public DatabaseStorageProvider(
            JdbcTemplate db,
            @Value("${cloud.storage.db-max-file-bytes:4194304}") long maxFileBytes) {
        this.db = db;
        this.maxFileBytes = Math.max(64 * 1024L, maxFileBytes);
    }

    @Override
    public String providerType() {
        return "DB";
    }

    @Override
    public StoredObject put(String key, InputStream input, long maxBytes) throws IOException {
        long effectiveLimit = Math.min(maxBytes, maxFileBytes);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MessageDigest sha;
        try { sha = MessageDigest.getInstance("SHA-256"); }
        catch (Exception e) { throw new IOException(e); }

        long total = 0;
        byte[] buf = new byte[8192];
        try (DigestInputStream din = new DigestInputStream(input, sha)) {
            int n;
            while ((n = din.read(buf)) >= 0) {
                total += n;
                if (total > effectiveLimit) {
                    throw new BizException(HttpStatus.PAYLOAD_TOO_LARGE, 413,
                            "数据库存储模式单文件不能超过 " + (maxFileBytes / 1024 / 1024) + " MB");
                }
                out.write(buf, 0, n);
            }
        }
        String resolvedKey = (key == null || key.isBlank())
                ? "db/" + UUID.randomUUID().toString().replace("-", "")
                : key;
        String hash = HexFormat.of().formatHex(sha.digest());
        db.update(
                "INSERT INTO blob_db_content(storage_key, size_bytes, sha256, content) VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE size_bytes=VALUES(size_bytes), sha256=VALUES(sha256), content=VALUES(content)",
                resolvedKey, total, hash, out.toByteArray());
        return new StoredObject(resolvedKey, total, hash);
    }

    @Override
    public InputStream get(String key) throws IOException {
        var rows = db.queryForList("SELECT content FROM blob_db_content WHERE storage_key=?", key);
        if (rows.isEmpty()) throw new FileNotFoundException(key);
        return new ByteArrayInputStream((byte[]) rows.get(0).get("content"));
    }

    @Override
    public InputStream getRange(String key, long start, long endInclusive) throws IOException {
        var rows = db.queryForList("SELECT content FROM blob_db_content WHERE storage_key=?", key);
        if (rows.isEmpty()) throw new FileNotFoundException(key);
        byte[] all = (byte[]) rows.get(0).get("content");
        int s = (int) Math.max(0, Math.min(all.length, start));
        int e = (int) Math.max(s, Math.min(all.length, endInclusive + 1));
        return new ByteArrayInputStream(all, s, e - s);
    }

    @Override
    public boolean exists(String key) {
        Integer cnt = db.queryForObject("SELECT COUNT(*) FROM blob_db_content WHERE storage_key=?", Integer.class, key);
        return cnt != null && cnt > 0;
    }

    @Override
    public void delete(String key) {
        db.update("DELETE FROM blob_db_content WHERE storage_key=?", key);
    }

    @Override
    public StorageObjectInfo stat(String key) throws IOException {
        var rows = db.queryForList("SELECT size_bytes FROM blob_db_content WHERE storage_key=?", key);
        if (rows.isEmpty()) throw new FileNotFoundException(key);
        return new StorageObjectInfo(key, ((Number) rows.get(0).get("size_bytes")).longValue());
    }
}
