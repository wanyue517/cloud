package com.wanyue.cloud.modules.drive;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.drive.storage.StorageRouterService;
import com.wanyue.cloud.modules.ops.SystemPolicyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.file.*;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import static com.wanyue.cloud.modules.drive.DriveModels.*;

@Service
public class UploadService {
    private final JdbcTemplate db;
    private final DriveService drive;
    private final StorageRouterService storageRouter;
    private final SystemPolicyService policy;
    private final com.wanyue.cloud.modules.drive.storage.S3StorageProvider s3Provider;
    private final com.wanyue.cloud.modules.drive.storage.SecretCipher cipher;
    private final Path temp;

    public UploadService(
            JdbcTemplate db,
            DriveService drive,
            StorageRouterService storageRouter,
            SystemPolicyService policy,
            com.wanyue.cloud.modules.drive.storage.S3StorageProvider s3Provider,
            com.wanyue.cloud.modules.drive.storage.SecretCipher cipher,
            @Value("${cloud.storage.upload-temp:data/uploads}") String temp) throws IOException {
        this.db = db;
        this.drive = drive;
        this.storageRouter = storageRouter;
        this.policy = policy;
        this.s3Provider = s3Provider;
        this.cipher = cipher;
        this.temp = Path.of(temp).toAbsolutePath().normalize();
        Files.createDirectories(this.temp);
    }

    public Map<String, Object> presign(long user, long id, int expiresSeconds) {
        var s = session(user, id, false);
        long profileId = s.get("storage_profile_id") == null ? 1L : number(s, "storage_profile_id");
        var profile = storageRouter.getProfile(profileId);
        if (!"S3".equalsIgnoreCase(profile.provider())) {
            return Map.of(
                    "mode", "DIRECT_API",
                    "provider", profile.provider(),
                    "uploadUrl", "/api/v1/uploads/" + id + "/chunks/{index}",
                    "expiresInSeconds", Math.max(60, Math.min(3600, expiresSeconds)));
        }
        String key = "uploads/" + id + "/" + UUID.randomUUID().toString().replace("-", "");
        String secret = cipher.decrypt(profile.secretCipher());
        String presignedUrl = s3Provider.generatePresignedPutUrl(
                profile.endpoint(), profile.bucket(), profile.region(),
                profile.accessKey(), secret, key, expiresSeconds);
        return Map.of(
                "mode", "S3_PRESIGNED_PUT",
                "provider", "S3",
                "objectKey", key,
                "uploadUrl", presignedUrl,
                "expiresInSeconds", Math.max(60, Math.min(3600, expiresSeconds)));
    }

    @Transactional
    public Upload create(long user, UploadInput input) throws IOException {
        drive.access(user, input.spaceId(), true);
        DriveService.validName(input.name());
        drive.ancestors(input.spaceId(), input.parentId());
        drive.checkUploadPolicy(input.name(), input.size());
        drive.checkQuota(user, input.size());
        int maxConcurrent = policy.uploadMaxConcurrentSessions();
        long active = db.queryForObject(
                "SELECT COUNT(*) FROM upload_session WHERE user_id=? AND status IN ('UPLOADING','QUEUED','PROCESSING') AND (expires_at>UTC_TIMESTAMP(3) OR status IN ('QUEUED','PROCESSING'))",
                Long.class, user);
        if (active >= maxConcurrent) {
            throw new BizException(HttpStatus.TOO_MANY_REQUESTS, 429, "同时上传任务不能超过 " + maxConcurrent + " 个");
        }
        int chunkSize = policy.uploadChunkSizeBytes();
        int ttlHours = policy.uploadSessionTtlHours();
        long profileId = storageRouter.resolveProfileIdForUsage(StorageRouterService.USAGE_USER_FILES);
        long id = IdWorker.getId();
        int count = (int) Math.max(1, (input.size() + chunkSize - 1) / chunkSize);

        // M039: 用户作用域 SHA-256 秒传去重（仅复用当前用户自身可读物理对象）
        if (input.expectedSha256() != null && !input.expectedSha256().isBlank()) {
            var existingBlob = drive.findUserReadableBlobBySha256(user, input.expectedSha256(), input.size());
            if (existingBlob.isPresent()) {
                Node instantNode = drive.instantCreateFromBlob(
                        user, input.spaceId(), input.parentId(), input.name(), existingBlob.get(), input.size());
                db.update(
                        "INSERT INTO upload_session(id,user_id,space_id,storage_profile_id,parent_id,name,size_bytes,chunk_size,chunk_count,mime_type,status,file_id,expires_at) "
                                + "VALUES (?,?,?,?,?,?,?,?,?,?,'COMPLETED',?,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL ? HOUR))",
                        id, user, input.spaceId(), profileId, input.parentId(), input.name(), input.size(), chunkSize, count,
                        DriveService.safeMime(input.name()), Long.parseLong(instantNode.id()), ttlHours);
                List<Integer> allParts = new ArrayList<>(count);
                for (int i = 0; i < count; i++) allParts.add(i);
                return new Upload(String.valueOf(id), chunkSize, count, allParts, List.of(), "COMPLETED", instantNode.id(), true);
            }
        }

        db.update(
                "INSERT INTO upload_session(id,user_id,space_id,storage_profile_id,parent_id,name,size_bytes,chunk_size,chunk_count,mime_type,expires_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL ? HOUR))",
                id, user, input.spaceId(), profileId, input.parentId(), input.name(), input.size(), chunkSize, count,
                DriveService.safeMime(input.name()), ttlHours);
        return status(user, id, false);
    }

    private Map<String, Object> session(long user, long id, boolean lock) {
        var rows = db.queryForList(
                "SELECT * FROM upload_session WHERE id=? AND user_id=? AND (expires_at>UTC_TIMESTAMP(3) OR status IN ('QUEUED','PROCESSING'))" + (lock ? " FOR UPDATE" : ""),
                id, user);
        if (rows.isEmpty()) throw BizException.notFound("上传任务不存在或已过期");
        var s = rows.get(0);
        drive.access(user, number(s, "space_id"), false);
        return s;
    }

    private static long number(Map<String, Object> s, String key) {
        return ((Number) s.get(key)).longValue();
    }

    public Upload status(long user, long id, boolean lock) throws IOException {
        var s = session(user, id, lock);
        int count = (int) number(s, "chunk_count");
        int chunkSize = (int) number(s, "chunk_size");
        List<Integer> received = new ArrayList<>();
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (Set.of("COMPLETED", "QUEUED", "PROCESSING").contains(s.get("status")) || Files.isRegularFile(part(id, i))) received.add(i);
            else missing.add(i);
        }
        return new Upload(String.valueOf(id), chunkSize, count, received, missing,
                (String) s.get("status"), s.get("file_id") == null ? null : s.get("file_id").toString(), false,
                (String) s.get("completion_error"));
    }

    private Path part(long id, int index) {
        return temp.resolve(id + "-" + index);
    }

    @Transactional(rollbackFor = Exception.class)
    public Upload requestCompletion(long user, long id, String expectedSha256) throws IOException {
        var s = session(user, id, true);
        String state = (String) s.get("status");
        if (Set.of("COMPLETED", "QUEUED", "PROCESSING").contains(state)) return status(user, id, false);
        if (!Set.of("UPLOADING", "FAILED").contains(state)) throw BizException.conflict("上传已取消");
        if (expectedSha256 != null && !expectedSha256.matches("[0-9a-fA-F]{64}")) {
            throw new BizException(HttpStatus.BAD_REQUEST, 400, "文件 SHA-256 格式无效");
        }
        int count = (int) number(s, "chunk_count");
        for (int i = 0; i < count; i++) if (!Files.isRegularFile(part(id, i))) throw BizException.conflict("仍有分片未上传");
        drive.resolveNodeAccess(user, number(s, "space_id"), number(s, "parent_id"), true, true);
        drive.checkQuota(user, number(s, "size_bytes"));
        String token = com.wanyue.cloud.modules.drive.personal.PrivateTokenFilter.currentToken();
        db.update("UPDATE upload_session SET status='QUEUED',completion_requested_at=UTC_TIMESTAMP(3),completion_sha256=?,completion_error=NULL,completion_private_token=? WHERE id=?",
                expectedSha256, cipher.encrypt(token), id);
        return status(user, id, false);
    }

    @Transactional
    public Map<String, Object> beginCompletion(long user, long id) {
        var s = session(user, id, true);
        if (!Set.of("QUEUED", "PROCESSING").contains(s.get("status"))) return null;
        drive.resolveNodeAccess(user, number(s, "space_id"), number(s, "parent_id"), true, false);
        db.update("UPDATE upload_session SET status='PROCESSING' WHERE id=?", id);
        return s;
    }

    public DriveService.PreparedUpload prepareCompletion(long id, Map<String, Object> s) throws IOException {
        int count = (int) number(s, "chunk_count");
        drive.checkUploadPolicy((String) s.get("name"), number(s, "size_bytes"));
        drive.checkQuota(number(s, "user_id"), number(s, "size_bytes"));
        long profile = s.get("storage_profile_id") == null ? 1L : number(s, "storage_profile_id");
        try (InputStream input = combinedStream(id, count)) {
            return drive.prepareUpload(input, number(s, "size_bytes"), (String) s.get("completion_sha256"), profile);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void finishCompletion(long user, long id, DriveService.PreparedUpload prepared) {
        var s = session(user, id, true);
        if (!"PROCESSING".equals(s.get("status"))) throw BizException.conflict("上传处理状态已变更");
        Node node = drive.commitPreparedUpload(user, number(s, "space_id"), number(s, "parent_id"),
                (String) s.get("name"), (String) s.get("mime_type"), null, null, prepared);
        db.update("UPDATE upload_session SET status='COMPLETED',file_id=?,completion_private_token=NULL,expires_at=DATE_ADD(UTC_TIMESTAMP(3), INTERVAL ? HOUR) WHERE id=?",
                Long.parseLong(node.id()), policy.uploadSessionTtlHours(), id);
    }

    public boolean cleanupCompletion(long id, int count) { return cleanup(id, count); }

    private InputStream combinedStream(long id, int count) {
        return new InputStream() {
            private int index;
            private InputStream current;
            @Override public int read() throws IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) == -1 ? -1 : one[0] & 255;
            }
            @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("上传处理已中断");
                if (length == 0) return 0;
                while (index < count) {
                    if (current == null) current = Files.newInputStream(part(id, index));
                    int read = current.read(buffer, offset, length);
                    if (read != -1) return read;
                    current.close(); current = null; index++;
                }
                return -1;
            }
            @Override public void close() throws IOException { if (current != null) current.close(); }
        };
    }

    @Transactional(rollbackFor = Exception.class)
    public void chunk(long user, long id, int index, InputStream stream, long size) throws IOException {
        chunk(user, id, index, stream, size, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void chunk(long user, long id, int index, InputStream stream, long size, String expectedSha256) throws IOException {
        var s = session(user, id, true);
        int count = (int) number(s, "chunk_count");
        long sessionChunkSize = number(s, "chunk_size");
        if (!Set.of("UPLOADING", "FAILED").contains(s.get("status"))) throw BizException.conflict("上传已结束");
        if (index < 0 || index >= count) throw new BizException(HttpStatus.BAD_REQUEST, 400, "分片编号无效");
        long expected = index == count - 1 ? number(s, "size_bytes") - (long) index * sessionChunkSize : sessionChunkSize;
        if (size != expected) throw new BizException(HttpStatus.BAD_REQUEST, 400, "分片大小不正确");
        Path staging = Files.createTempFile(temp, "chunk-", ".tmp");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var out = Files.newOutputStream(staging)) {
                byte[] buffer = new byte[65536];
                long bytes = 0;
                int n;
                while ((n = stream.read(buffer)) != -1) {
                    bytes += n;
                    if (bytes > expected) throw new IOException("分片超长");
                    out.write(buffer, 0, n);
                    digest.update(buffer, 0, n);
                }
                if (bytes != expected) throw new IOException("分片不完整");
            }
            if (expectedSha256 != null && !expectedSha256.isBlank()) {
                String actual = HexFormat.of().formatHex(digest.digest());
                if (!actual.equalsIgnoreCase(expectedSha256.strip())) {
                    throw new BizException(HttpStatus.BAD_REQUEST, 400, "分片内容校验失败");
                }
            }
            Files.move(staging, part(id, index), StandardCopyOption.REPLACE_EXISTING);
            if ("FAILED".equals(s.get("status"))) {
                db.update("UPDATE upload_session SET status='UPLOADING',completion_error=NULL WHERE id=?", id);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } finally {
            Files.deleteIfExists(staging);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public String complete(long user, long id) throws IOException {
        return complete(user, id, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public String complete(long user, long id, String expectedSha256) throws IOException {
        var s = session(user, id, true);
        if (s.get("status").equals("COMPLETED")) return s.get("file_id").toString();
        if (!s.get("status").equals("UPLOADING")) throw BizException.conflict("上传已取消");
        int count = (int) number(s, "chunk_count");
        for (int i = 0; i < count; i++) if (!Files.isRegularFile(part(id, i))) throw BizException.conflict("仍有分片未上传");
        long lockedProfileId = s.get("storage_profile_id") == null ? 1L : number(s, "storage_profile_id");
        Node node;
        try (var combined = combinedStream(id, count)) {
            node = drive.upload(user, number(s, "space_id"), number(s, "parent_id"), (String) s.get("name"),
                    (String) s.get("mime_type"), combined, number(s, "size_bytes"), null, null, expectedSha256, lockedProfileId);
        }
        db.update("UPDATE upload_session SET status='COMPLETED',file_id=? WHERE id=?", Long.parseLong(node.id()), id);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cleanup(id, count); }
        });
        return node.id();
    }

    @Transactional
    public void cancel(long user, long id) {
        var s = session(user, id, true);
        if (s.get("status").equals("COMPLETED")) throw BizException.conflict("上传已完成");
        db.update("UPDATE upload_session SET status='CANCELLED',completion_private_token=NULL WHERE id=?", id);
        cleanup(id, (int) number(s, "chunk_count"));
    }

    private boolean cleanup(long id, int count) {
        boolean success = true;
        for (int i = 0; i < count; i++) try { Files.deleteIfExists(part(id, i)); } catch (IOException e) { success = false; }
        return success;
    }

    public List<Map<String, Object>> listByUser(long user) {
        var rows = db.queryForList(
                "SELECT CAST(id AS CHAR) id, CAST(space_id AS CHAR) spaceId, name, size_bytes sizeBytes, "
                        + "chunk_size chunkSize, chunk_count chunkCount, status, CAST(file_id AS CHAR) fileId, "
                        + "created_at createdAt, expires_at expiresAt "
                        + "FROM upload_session WHERE user_id=? ORDER BY created_at DESC LIMIT 100",
                user);
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (var row : rows) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            long sid = Long.parseLong(String.valueOf(row.get("id")));
            int count = ((Number) row.get("chunkCount")).intValue();
            int receivedCount = 0;
            if ("COMPLETED".equals(row.get("status"))) {
                receivedCount = count;
            } else {
                for (int i = 0; i < count; i++) {
                    if (Files.isRegularFile(part(sid, i))) receivedCount++;
                }
            }
            item.put("receivedCount", receivedCount);
            result.add(item);
        }
        return result;
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
    @Transactional
    public void cleanExpired() {
        cleanExpiredAndCount();
    }

    @Transactional
    public int cleanExpiredAndCount() {
        var expired = db.queryForList("SELECT id,chunk_count FROM upload_session WHERE expires_at<UTC_TIMESTAMP(3) AND status NOT IN ('EXPIRED','QUEUED','PROCESSING') LIMIT 100 FOR UPDATE SKIP LOCKED");
        int cleaned = 0;
        for (var session : expired) {
            long id = number(session, "id");
            if (cleanup(id, (int) number(session, "chunk_count"))) {
                db.update("UPDATE upload_session SET status='EXPIRED' WHERE id=?", id);
                cleaned++;
            }
        }
        return cleaned;
    }
}
