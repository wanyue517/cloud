package com.wanyue.cloud.modules.drive.personal;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.drive.DriveModels;
import com.wanyue.cloud.modules.drive.DriveService;
import com.wanyue.cloud.modules.drive.LocalStorage;
import com.wanyue.cloud.modules.drive.sharelink.ShareLinkService;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class PersonalAndBatchService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final tools.jackson.databind.json.JsonMapper JSON = new tools.jackson.databind.json.JsonMapper();
    private static final long MAX_ZIP_BYTES = 500L * 1024 * 1024;

    private final JdbcTemplate db;
    private final DriveService drive;
    private final LocalStorage storage;
    private final PasswordEncoder passwordEncoder;
    private final Path workerTmpDir;

    public PersonalAndBatchService(
            JdbcTemplate db,
            DriveService drive,
            LocalStorage storage,
            PasswordEncoder passwordEncoder,
            @Value("${cloud.storage.worker-tmp:data/worker-tmp}") String workerTmp) throws IOException {
        this.db = db;
        this.drive = drive;
        this.storage = storage;
        this.passwordEncoder = passwordEncoder;
        this.workerTmpDir = Path.of(workerTmp).toAbsolutePath().normalize();
        Files.createDirectories(this.workerTmpDir);
    }

    public record UnlockPrivateInput(@NotBlank @Size(max = 128) String password, @Min(1) @Max(120) Integer idleMinutes) {}
    public record UnlockPrivateResult(String privateToken, String expiresAt, int idleMinutes) {}

    public record BatchExecuteInput(
            @Pattern(regexp = "MOVE|COPY|DELETE") String action,
            @NotEmpty @Size(max = 200) List<String> nodeIds,
            Long targetSpaceId,
            Long targetParentId) {}

    public record BatchItemOutcome(String nodeId, boolean ok, String message) {}
    public record BatchJobView(
            String id,
            String jobType,
            String status,
            int totalCount,
            int successCount,
            int failedCount,
            List<BatchItemOutcome> outcomes,
            String errorMessage) {}

    public record ZipBatchInput(@NotEmpty @Size(max = 200) List<String> nodeIds) {}

    public record UserPreferences(
            String theme,
            String density,
            String fileView,
            String fileSort,
            String defaultSpaceId) {}

    public record RecentAccessItem(
            String id,
            String spaceId,
            String spaceName,
            String parentId,
            String name,
            String nodeType,
            long size,
            String mimeType,
            String accessedAt) {}

    public record FileProgressInput(
            @NotBlank @Pattern(regexp = "VIDEO|PDF|AUDIO") String progressType,
            @DecimalMin("0.0") double positionValue) {}

    public record FileProgressView(
            String nodeId,
            String progressType,
            double positionValue,
            String updatedAt) {}

    @Transactional
    public UnlockPrivateResult unlockPrivateSpace(long user, long spaceId, UnlockPrivateInput input) {
        var spaceRows = db.queryForList(
                "SELECT id FROM space WHERE id=? AND owner_user_id=? AND kind='PRIVATE' AND deleted=0 AND status='ACTIVE'",
                spaceId, user);
        if (spaceRows.isEmpty()) throw BizException.notFound("私密空间不存在");
        return issuePrivateSession(user, spaceId, input);
    }

    /** 个人中心「私密设置」全局解锁：验证登录密码后签发 space_id=0 的短期会话，可访问全部私密空间与私密条目。 */
    @Transactional
    public UnlockPrivateResult unlockVault(long user, UnlockPrivateInput input) {
        return issuePrivateSession(user, 0L, input);
    }

    /** 一键锁定：撤销当前用户全部私密会话。 */
    @Transactional
    public void lockVault(long user) {
        db.update("UPDATE private_space_session SET revoked_at=UTC_TIMESTAMP(3) WHERE user_id=? AND revoked_at IS NULL", user);
    }

    public record PrivateSpaceItem(String id, String name, String kind, String color, boolean privateEnabled) {}
    public record PrivateNodeItem(
            String id, String spaceId, String spaceName, String parentId, String name, String nodeType,
            String currentVersionId, long revision, long size, String mimeType, String updatedAt) {}
    public record PrivateOverview(List<PrivateSpaceItem> spaces, List<PrivateNodeItem> items) {}

    public PrivateOverview privateOverview(long user) {
        drive.requireVaultSession(user);
        List<PrivateSpaceItem> spaces = db.query(
                "SELECT id, name, kind, color FROM space WHERE owner_user_id=? AND deleted=0 AND status='ACTIVE' "
                        + "AND kind IN ('PERSONAL','PRIVATE') ORDER BY sort_order, id",
                (r, i) -> new PrivateSpaceItem(r.getString("id"), r.getString("name"), r.getString("kind"),
                        r.getString("color"), "PRIVATE".equals(r.getString("kind"))),
                user);
        List<PrivateNodeItem> items = db.query(
                "SELECT n.id, n.space_id, s.name space_name, n.parent_id, n.name, n.node_type, n.current_version_id, n.revision, "
                        + "COALESCE(b.size_bytes, 0) size_bytes, COALESCE(v.mime_type, '') mime_type, n.updated_at "
                        + "FROM file_node n JOIN space s ON s.id=n.space_id AND s.deleted=0 AND s.status='ACTIVE' "
                        + "LEFT JOIN space_member m ON m.space_id=s.id AND m.user_id=? AND m.deleted=0 "
                        + "  AND (m.expires_at IS NULL OR m.expires_at > UTC_TIMESTAMP(3)) "
                        + "LEFT JOIN file_version v ON v.id=n.current_version_id "
                        + "LEFT JOIN blob_object b ON b.id=v.blob_id "
                        + "WHERE n.hidden=TRUE AND n.deleted_at IS NULL "
                        + "  AND (s.owner_user_id=? OR (s.kind='SHARED' AND m.role IN ('OWNER','MANAGER','EDITOR'))) "
                        + "ORDER BY n.updated_at DESC, n.id DESC LIMIT 500",
                (r, i) -> new PrivateNodeItem(
                        r.getString("id"), r.getString("space_id"), r.getString("space_name"), r.getString("parent_id"),
                        r.getString("name"), r.getString("node_type"), r.getString("current_version_id"), r.getLong("revision"),
                        r.getLong("size_bytes"), r.getString("mime_type"),
                        r.getObject("updated_at", LocalDateTime.class).toInstant(ZoneOffset.UTC).toString()),
                user, user);
        return new PrivateOverview(spaces, items);
    }

    /** 单独开启/关闭某个个人空间的私密保护（PERSONAL ⇄ PRIVATE），需已解锁私密设置。 */
    @Transactional
    public void setSpacePrivate(long user, long spaceId, boolean enabled) {
        drive.requireVaultSession(user);
        var rows = db.queryForList(
                "SELECT kind FROM space WHERE id=? AND owner_user_id=? AND deleted=0 AND status='ACTIVE' FOR UPDATE", spaceId, user);
        if (rows.isEmpty()) throw BizException.notFound("空间不存在或仅所有者可操作");
        String kind = String.valueOf(rows.get(0).get("kind"));
        if ("SHARED".equals(kind)) throw new BizException(HttpStatus.BAD_REQUEST, 400, "共享空间不支持设为私密");
        String next = enabled ? "PRIVATE" : "PERSONAL";
        if (next.equals(kind)) return;
        db.update("UPDATE space SET kind=?, version=version+1, access_version=access_version+1 WHERE id=?", next, spaceId);
        if (enabled) {
            // 私密空间隔离分享、收藏、快捷访问与最近使用
            db.update("UPDATE share_link SET disabled_at=UTC_TIMESTAMP(3) WHERE space_id=? AND disabled_at IS NULL", spaceId);
            db.update("UPDATE resource_share SET revoked_at=UTC_TIMESTAMP(3) WHERE space_id=? AND revoked_at IS NULL", spaceId);
            db.update("DELETE f FROM file_favorite f JOIN file_node n ON n.id=f.file_id WHERE n.space_id=?", spaceId);
            db.update("DELETE FROM user_quick_access WHERE space_id=?", spaceId);
            db.update("DELETE FROM user_recent_access WHERE space_id=?", spaceId);
        } else {
            db.update("UPDATE private_space_session SET revoked_at=UTC_TIMESTAMP(3) WHERE space_id=? AND revoked_at IS NULL", spaceId);
        }
    }

    private UnlockPrivateResult issuePrivateSession(long user, long spaceId, UnlockPrivateInput input) {
        var credRows = db.queryForList("SELECT password_hash FROM auth_password_credential WHERE user_id=? AND deleted=0 LIMIT 1", user);
        if (credRows.isEmpty()) throw new BizException(HttpStatus.BAD_REQUEST, 400, "当前账号未设置密码");
        if (!passwordEncoder.matches(input.password(), String.valueOf(credRows.get(0).get("password_hash")))) {
            throw new BizException(HttpStatus.FORBIDDEN, 403, "密码错误，无法解锁私密内容");
        }
        int idle = input.idleMinutes() == null ? 15 : Math.max(1, Math.min(120, input.idleMinutes()));
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String hash = ShareLinkService.sha256Hex(token);
        LocalDateTime expires = LocalDateTime.ofInstant(Instant.now().plusSeconds(idle * 60L), ZoneOffset.UTC);

        db.update(
                "INSERT INTO private_space_session(id, space_id, user_id, token_hash, idle_timeout_minutes, expires_at) VALUES (?, ?, ?, ?, ?, ?)",
                IdWorker.getId(), spaceId, user, hash, idle, expires);
        return new UnlockPrivateResult(token, expires.toInstant(ZoneOffset.UTC).toString(), idle);
    }

    @Transactional
    public void lockPrivateSpace(long user, long spaceId) {
        db.update("UPDATE private_space_session SET revoked_at=UTC_TIMESTAMP(3) WHERE space_id=? AND user_id=? AND revoked_at IS NULL",
                spaceId, user);
    }

    public BatchJobView executeBatch(long user, long spaceId, BatchExecuteInput input) {
        drive.access(user, spaceId, false, true);
        long jobId = IdWorker.getId();
        List<BatchItemOutcome> outcomes = new ArrayList<>();
        int ok = 0, fail = 0;

        for (String rawId : input.nodeIds()) {
            try {
                long nid = Long.parseLong(rawId);
                DriveModels.Node n = drive.getNode(spaceId, nid, false);
                switch (input.action()) {
                    case "DELETE" -> drive.trash(user, spaceId, nid, n.revision());
                    case "MOVE" -> {
                        long ts = input.targetSpaceId() == null ? spaceId : input.targetSpaceId();
                        long tp = input.targetParentId() == null ? 0L : input.targetParentId();
                        drive.transfer(user, spaceId, nid, new DriveModels.TransferInput(ts, tp, "MOVE", n.revision()));
                    }
                    case "COPY" -> {
                        long ts = input.targetSpaceId() == null ? spaceId : input.targetSpaceId();
                        long tp = input.targetParentId() == null ? 0L : input.targetParentId();
                        drive.transfer(user, spaceId, nid, new DriveModels.TransferInput(ts, tp, "COPY", n.revision()));
                    }
                    default -> throw new BizException(HttpStatus.BAD_REQUEST, 400, "不支持的批量动作");
                }
                outcomes.add(new BatchItemOutcome(rawId, true, "成功"));
                ok++;
            } catch (Exception e) {
                outcomes.add(new BatchItemOutcome(rawId, false, e.getMessage() == null ? "操作失败" : e.getMessage()));
                fail++;
            }
        }
        String status = fail == 0 ? "COMPLETED" : (ok == 0 ? "FAILED" : "PARTIAL");
        db.update(
                "INSERT INTO batch_job(id, user_id, space_id, job_type, status, total_count, success_count, failed_count, result_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                jobId, user, spaceId, input.action(), status, input.nodeIds().size(), ok, fail, JSON.writeValueAsString(outcomes));
        return new BatchJobView(String.valueOf(jobId), input.action(), status, input.nodeIds().size(), ok, fail, outcomes, null);
    }

    public BatchJobView createZipDownloadJob(long user, long spaceId, ZipBatchInput input) throws IOException {
        drive.access(user, spaceId, false, false);
        long jobId = IdWorker.getId();
        Path zipPath = workerTmpDir.resolve("batch-" + jobId + ".zip");
        List<BatchItemOutcome> outcomes = new ArrayList<>();
        int ok = 0, fail = 0;
        long totalWritten = 0;
        Set<String> usedNames = new HashSet<>();

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (String rawId : input.nodeIds()) {
                try {
                    long nid = Long.parseLong(rawId);
                    DriveModels.Node n = drive.getNode(spaceId, nid, false);
                    if ("FILE".equals(n.nodeType())) {
                        DriveModels.Content c = drive.content(user, spaceId, nid, null);
                        if (totalWritten + c.size() > MAX_ZIP_BYTES) {
                            throw new BizException(HttpStatus.PAYLOAD_TOO_LARGE, 413, "打包总大小超出 500 MB 上限");
                        }
                        String entryName = uniqueZipName(n.name(), usedNames);
                        zos.putNextEntry(new ZipEntry(entryName));
                        try (InputStream in = Files.newInputStream(c.path())) {
                            in.transferTo(zos);
                        }
                        zos.closeEntry();
                        totalWritten += c.size();
                        outcomes.add(new BatchItemOutcome(rawId, true, entryName));
                        ok++;
                    } else {
                        outcomes.add(new BatchItemOutcome(rawId, false, "目录请进入后选择文件打包"));
                        fail++;
                    }
                } catch (Exception e) {
                    outcomes.add(new BatchItemOutcome(rawId, false, e.getMessage() == null ? "打包失败" : e.getMessage()));
                    fail++;
                }
            }
        }
        String status = ok > 0 ? (fail == 0 ? "COMPLETED" : "PARTIAL") : "FAILED";
        if (ok == 0) Files.deleteIfExists(zipPath);
        db.update(
                "INSERT INTO batch_job(id, user_id, space_id, job_type, status, total_count, success_count, failed_count, result_json, artifact_path) VALUES (?, ?, ?, 'ZIP_DOWNLOAD', ?, ?, ?, ?, ?, ?)",
                jobId, user, spaceId, status, input.nodeIds().size(), ok, fail, JSON.writeValueAsString(outcomes), ok > 0 ? zipPath.toString() : null);
        return new BatchJobView(String.valueOf(jobId), "ZIP_DOWNLOAD", status, input.nodeIds().size(), ok, fail, outcomes, ok == 0 ? "无可打包文件" : null);
    }

    public Path resolveZipArtifact(long user, long jobId) {
        var rows = db.queryForList("SELECT status, artifact_path FROM batch_job WHERE id=? AND user_id=? AND job_type='ZIP_DOWNLOAD'", jobId, user);
        if (rows.isEmpty() || rows.get(0).get("artifact_path") == null) {
            throw BizException.notFound("打包文件不存在或已过期");
        }
        Path p = Path.of(String.valueOf(rows.get(0).get("artifact_path"))).normalize();
        if (!p.startsWith(workerTmpDir) || !Files.isRegularFile(p)) {
            throw BizException.notFound("打包文件不存在");
        }
        return p;
    }

    public UserPreferences getPreferences(long user) {
        var rows = db.queryForList(
                "SELECT theme, density, file_view, file_sort, default_space_id FROM sys_user_setting WHERE user_id=? AND deleted=0 LIMIT 1",
                user);
        if (rows.isEmpty()) {
            return new UserPreferences("system", "comfortable", "list", "name", "");
        }
        var r = rows.get(0);
        return new UserPreferences(
                String.valueOf(r.get("theme")),
                String.valueOf(r.get("density")),
                String.valueOf(r.get("file_view")),
                String.valueOf(r.get("file_sort")),
                r.get("default_space_id") == null ? "" : String.valueOf(r.get("default_space_id")));
    }

    @Transactional
    public UserPreferences updatePreferences(long user, UserPreferences input) {
        Long defSpace = (input.defaultSpaceId() == null || input.defaultSpaceId().isBlank()) ? null : Long.parseLong(input.defaultSpaceId());
        Integer exists = db.queryForObject("SELECT COUNT(*) FROM sys_user_setting WHERE user_id=? AND deleted=0", Integer.class, user);
        if (exists == null || exists == 0) {
            db.update(
                    "INSERT INTO sys_user_setting(id, user_id, theme, density, file_view, file_sort, default_space_id, created_by, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    IdWorker.getId(), user, safeVal(input.theme(), "system"), safeVal(input.density(), "comfortable"),
                    safeVal(input.fileView(), "list"), safeVal(input.fileSort(), "name"), defSpace, user, user);
        } else {
            db.update(
                    "UPDATE sys_user_setting SET theme=?, density=?, file_view=?, file_sort=?, default_space_id=?, updated_by=?, version=version+1 WHERE user_id=? AND deleted=0",
                    safeVal(input.theme(), "system"), safeVal(input.density(), "comfortable"),
                    safeVal(input.fileView(), "list"), safeVal(input.fileSort(), "name"), defSpace, user, user);
        }
        return getPreferences(user);
    }

    @Transactional
    public void recordAccess(long user, long spaceId, long nodeId) {
        String kind = db.queryForObject("SELECT kind FROM space WHERE id=? AND deleted=0", String.class, spaceId);
        if ("PRIVATE".equals(kind)) return; // 私密空间不进入最近使用记录
        drive.resolveNodeAccess(user, spaceId, nodeId, false, false);
        db.update(
                "INSERT INTO user_recent_access(id, user_id, space_id, node_id, accessed_at) VALUES (?, ?, ?, ?, UTC_TIMESTAMP(3)) "
                        + "ON DUPLICATE KEY UPDATE accessed_at=UTC_TIMESTAMP(3)",
                IdWorker.getId(), user, spaceId, nodeId);
    }

    public List<RecentAccessItem> listRecent(long user) {
        var rows = db.queryForList(
                "SELECT r.id, r.space_id, s.name space_name, n.parent_id, n.id node_id, n.name, n.node_type, "
                        + "COALESCE(b.size_bytes, 0) size_bytes, COALESCE(v.mime_type, '') mime_type, r.accessed_at "
                        + "FROM user_recent_access r "
                        + "JOIN space s ON s.id=r.space_id AND s.deleted=0 AND s.status='ACTIVE' AND s.kind IN ('PERSONAL','SHARED') "
                        + "JOIN file_node n ON n.id=r.node_id AND n.deleted_at IS NULL AND n.hidden=FALSE "
                        + "LEFT JOIN file_version v ON v.id=n.current_version_id "
                        + "LEFT JOIN blob_object b ON b.id=v.blob_id "
                        + "WHERE r.user_id=? ORDER BY r.accessed_at DESC LIMIT 50",
                user);
        List<RecentAccessItem> out = new ArrayList<>();
        for (var r : rows) {
            long sid = ((Number) r.get("space_id")).longValue();
            long nid = ((Number) r.get("node_id")).longValue();
            try {
                drive.resolveNodeAccess(user, sid, nid, false, false);
            } catch (BizException ex) {
                continue;
            }
            String ts = r.get("accessed_at") instanceof LocalDateTime ldt ? ldt.toInstant(ZoneOffset.UTC).toString() : "";
            out.add(new RecentAccessItem(
                    String.valueOf(nid), String.valueOf(sid), String.valueOf(r.get("space_name")),
                    String.valueOf(r.get("parent_id")), String.valueOf(r.get("name")),
                    String.valueOf(r.get("node_type")), ((Number) r.get("size_bytes")).longValue(),
                    String.valueOf(r.get("mime_type")), ts));
        }
        return out;
    }

    public FileProgressView getFileProgress(long user, long spaceId, long nodeId) {
        drive.resolveNodeAccess(user, spaceId, nodeId, false, false);
        var rows = db.queryForList(
                "SELECT progress_type, position_value, updated_at FROM user_file_progress WHERE user_id=? AND node_id=?",
                user, nodeId);
        if (rows.isEmpty()) return null;
        var row = rows.get(0);
        String ts = row.get("updated_at") instanceof LocalDateTime ldt ? ldt.toInstant(ZoneOffset.UTC).toString() : "";
        return new FileProgressView(
                String.valueOf(nodeId),
                String.valueOf(row.get("progress_type")),
                ((Number) row.get("position_value")).doubleValue(),
                ts);
    }

    @Transactional
    public FileProgressView saveFileProgress(long user, long spaceId, long nodeId, FileProgressInput input) {
        drive.resolveNodeAccess(user, spaceId, nodeId, false, false);
        double pos = Double.isFinite(input.positionValue()) && input.positionValue() >= 0 ? input.positionValue() : 0.0;
        db.update(
                "INSERT INTO user_file_progress(id, user_id, node_id, progress_type, position_value, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, UTC_TIMESTAMP(3)) "
                        + "ON DUPLICATE KEY UPDATE progress_type=VALUES(progress_type), position_value=VALUES(position_value), updated_at=UTC_TIMESTAMP(3)",
                IdWorker.getId(), user, nodeId, input.progressType(), pos);
        return getFileProgress(user, spaceId, nodeId);
    }

    private static String uniqueZipName(String name, Set<String> used) {
        if (used.add(name)) return name;
        for (int i = 1; i < 1000; i++) {
            String candidate = "(" + i + ")_" + name;
            if (used.add(candidate)) return candidate;
        }
        return UUID.randomUUID() + "_" + name;
    }

    private static String safeVal(String v, String def) {
        return (v == null || v.isBlank()) ? def : v.strip();
    }
}
