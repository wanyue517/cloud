package com.wanyue.cloud.modules.drive;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.drive.storage.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;

/** Control plane only: object bytes go from the client to S3, never through this service. */
@Service
public class DirectUploadService {
    private final JdbcTemplate db;
    private final DriveService drive;
    private final StorageRouterService storage;
    private final S3StorageProvider s3;
    private final SecretCipher cipher;

    public DirectUploadService(JdbcTemplate db, DriveService drive, StorageRouterService storage, S3StorageProvider s3, SecretCipher cipher) {
        this.db = db; this.drive = drive; this.storage = storage; this.s3 = s3; this.cipher = cipher;
    }

    static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    static boolean enabled(Object value) { return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue() != 0; }

    public Map<String, Object> owned(long user, long id, boolean lock) {
        var rows = db.queryForList("SELECT * FROM upload_session WHERE id=? AND user_id=? AND (expires_at>UTC_TIMESTAMP(3) OR status IN ('QUEUED','PROCESSING'))" + (lock ? " FOR UPDATE" : ""), id, user);
        if (rows.isEmpty()) throw BizException.notFound("上传任务不存在或已过期");
        var row = rows.get(0);
        drive.resolveNodeAccess(user, number(row, "space_id"), number(row, "parent_id"), true, false);
        return row;
    }

    private void writable(Map<String, Object> row) {
        if (!"DIRECT".equals(row.get("upload_mode"))) throw BizException.conflict("此任务使用后端中转");
        if (!Set.of("UPLOADING", "FAILED").contains(row.get("status"))) throw BizException.conflict("上传任务当前不可写入");
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> info(long user, long id) throws IOException {
        var row = owned(user, id, true);
        writable(row);
        var profile = storage.getProfile(number(row, "storage_profile_id"));
        if (!"S3".equals(profile.provider())) throw BizException.conflict("存储实例不再支持直传");
        String secret = cipher.decrypt(profile.secretCipher());
        String key = (String) row.get("direct_key");
        String uploadId = (String) row.get("direct_upload_id");
        if (uploadId == null) {
            key = "objects/" + UUID.randomUUID().toString().replace("-", "");
            uploadId = s3.initiateMultipart(profile, secret, key);
            db.update("UPDATE upload_session SET direct_key=?,direct_upload_id=? WHERE id=?", key, uploadId, id);
        }
        java.util.List<S3StorageProvider.RemotePart> parts;
        try { parts = s3.listParts(profile, secret, key, uploadId); }
        catch (S3StorageProvider.S3ResponseException e) {
            if (e.status != 404 || s3.headObjectSize(profile, secret, key) != number(row, "size_bytes")) throw e;
            // Remote completion succeeded before a failed/lost metadata commit; skip retransmission.
            var all = java.util.stream.IntStream.range(0, (int) number(row, "chunk_count")).boxed().toList();
            return Map.of("received", all, "concurrency", number(row, "direct_concurrency"),
                    "fallbackAllowed", enabled(row.get("direct_fallback")));
        }
        // Only advertise correctly sized parts as received; an interrupted/incorrect part is replaced.
        var received = new ArrayList<Integer>();
        for (var part : parts) {
            int index = part.number() - 1;
            if (index >= 0 && index < number(row, "chunk_count") && part.size() == partSize(row, index)) received.add(index);
        }
        return Map.of("received", received, "concurrency", number(row, "direct_concurrency"),
                "fallbackAllowed", enabled(row.get("direct_fallback")));
    }

    public Map<String, Object> presign(long user, long id, int index) throws IOException {
        var row = owned(user, id, false);
        writable(row);
        if (index < 0 || index >= number(row, "chunk_count")) throw BizException.conflict("分片编号无效");
        if (row.get("direct_upload_id") == null) throw BizException.conflict("请先初始化直传任务");
        var profile = storage.getProfile(number(row, "storage_profile_id"));
        String url = s3.presignPart(profile, cipher.decrypt(profile.secretCipher()), (String) row.get("direct_key"),
                (String) row.get("direct_upload_id"), index + 1, (int) number(row, "direct_url_ttl"));
        return Map.of("uploadUrl", url);
    }

    private static long partSize(Map<String, Object> row, int index) {
        return Math.min(number(row, "chunk_size"), number(row, "size_bytes") - index * number(row, "chunk_size"));
    }

    public DriveService.PreparedUpload prepare(Map<String, Object> row) throws IOException {
        long profileId = number(row, "storage_profile_id");
        var profile = storage.getProfile(profileId);
        String secret = cipher.decrypt(profile.secretCipher());
        String key = (String) row.get("direct_key");
        String uploadId = (String) row.get("direct_upload_id");
        if (key == null || uploadId == null) throw BizException.conflict("直传任务尚未初始化");
        drive.checkUploadPolicy((String) row.get("name"), number(row, "size_bytes"));
        if (profile.maxFileBytes() != null && number(row, "size_bytes") > profile.maxFileBytes()) throw BizException.conflict("文件超过存储实例大小限制");
        try {
            var parts = new ArrayList<>(s3.listParts(profile, secret, key, uploadId));
            parts.sort(Comparator.comparingInt(S3StorageProvider.RemotePart::number));
            if (parts.size() != number(row, "chunk_count")) throw BizException.conflict("直传分片不完整，请继续上传");
            for (int i = 0; i < parts.size(); i++) {
                if (parts.get(i).number() != i + 1 || parts.get(i).size() != partSize(row, i)) throw BizException.conflict("直传分片大小或编号不匹配");
            }
            s3.completeMultipart(profile, secret, key, uploadId, parts);
        } catch (S3StorageProvider.S3ResponseException e) {
            // Completion may have succeeded before a crash or a lost response. HEAD makes retry idempotent.
            if (e.status != 404) throw e;
        }
        if (s3.headObjectSize(profile, secret, key) != number(row, "size_bytes")) throw BizException.conflict("直传文件大小不匹配");
        // Multipart ETag and client hashes are not trusted full-file SHA-256 values.
        return new DriveService.PreparedUpload(profileId, new StorageProvider.StoredObject(key, number(row, "size_bytes"), null), true);
    }

    public void abort(Map<String, Object> row) throws IOException {
        if (row.get("direct_upload_id") == null) return;
        var p = storage.getProfile(number(row, "storage_profile_id"));
        s3.abortMultipart(p, cipher.decrypt(p.secretCipher()), (String) row.get("direct_key"), (String) row.get("direct_upload_id"));
        // Also clear objects completed remotely but never committed to a file node.
        s3.deleteObject(p.endpoint(), p.bucket(), p.region(), p.accessKey(), cipher.decrypt(p.secretCipher()), (String) row.get("direct_key"));
    }

    @Transactional(rollbackFor = Exception.class)
    public void fallback(long user, long id, int chunkSize) throws IOException {
        var row = owned(user, id, true);
        writable(row);
        if (!enabled(row.get("direct_fallback"))) throw BizException.conflict("管理员未允许直传失败回退");
        abort(row);
        long count = Math.max(1, (number(row, "size_bytes") + chunkSize - 1) / chunkSize);
        db.update("UPDATE upload_session SET upload_mode='PROXY',status='UPLOADING',chunk_size=?,chunk_count=?,direct_key=NULL,direct_upload_id=NULL,completion_error=NULL WHERE id=?", chunkSize, count, id);
    }
}
