package com.wanyue.cloud.modules.drive.sharelink;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.drive.DriveModels;
import com.wanyue.cloud.modules.drive.DriveService;
import com.wanyue.cloud.modules.drive.sharelink.ShareLinkModels.*;
import com.wanyue.cloud.modules.drive.storage.StorageRouterService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class ShareLinkService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(10);

    private final JdbcTemplate db;
    private final DriveService drive;
    private final StorageRouterService storageRouter;

    public ShareLinkService(JdbcTemplate db, DriveService drive, StorageRouterService storageRouter) {
        this.db = db;
        this.drive = drive;
        this.storageRouter = storageRouter;
    }

    public static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String generateHighEntropyToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String toIso(Object ts) {
        if (ts == null) return "";
        if (ts instanceof LocalDateTime ldt) return ldt.toInstant(ZoneOffset.UTC).toString();
        return String.valueOf(ts);
    }

    @Transactional
    public CreatedShareLink create(long user, long spaceId, long nodeId, CreateShareLinkInput input) {
        drive.resolveNodeAccess(user, spaceId, nodeId, true, false);
        DriveModels.Node node = drive.getNode(spaceId, nodeId, false);
        if (node.hidden()) {
            throw BizException.conflict("隐藏文件或文件夹不能创建公开分享链接");
        }
        String token = generateHighEntropyToken();
        String tokenHash = sha256Hex(token);
        String prefix = token.substring(0, Math.min(8, token.length()));
        String rawPwd = input.password() == null ? "" : input.password().strip();
        String pwdHash = rawPwd.isEmpty() ? null : BCRYPT.encode(rawPwd);
        boolean allowDownload = input.allowDownload() == null || input.allowDownload();
        LocalDateTime expiresAt = (input.expireDays() != null && input.expireDays() > 0)
                ? LocalDateTime.ofInstant(Instant.now().plusSeconds(input.expireDays() * 86400L), ZoneOffset.UTC)
                : null;

        long id = IdWorker.getId();
        db.update(
                "INSERT INTO share_link(id, space_id, node_id, creator_user_id, token_hash, token_prefix, password_hash, allow_download, max_downloads, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, spaceId, nodeId, user, tokenHash, prefix, pwdHash, allowDownload ? 1 : 0, input.maxDownloads(), expiresAt);
        return new CreatedShareLink(
                String.valueOf(id),
                token,
                "/s/" + token,
                pwdHash != null,
                allowDownload,
                input.maxDownloads(),
                expiresAt == null ? "" : expiresAt.toInstant(ZoneOffset.UTC).toString());
    }

    public List<ShareLinkItem> listMyLinks(long user) {
        return db.query(
                "SELECT sl.*, COALESCE(n.name, '(已删除)') node_name, COALESCE(n.node_type, 'FILE') node_type "
                        + "FROM share_link sl LEFT JOIN file_node n ON n.id=sl.node_id "
                        + "WHERE sl.creator_user_id=? ORDER BY sl.created_at DESC LIMIT 200",
                (r, i) -> new ShareLinkItem(
                        r.getString("id"),
                        r.getString("space_id"),
                        r.getString("node_id"),
                        r.getString("node_name"),
                        r.getString("node_type"),
                        r.getString("token_prefix"),
                        r.getString("password_hash") != null,
                        r.getBoolean("allow_download"),
                        r.getObject("max_downloads") == null ? null : r.getInt("max_downloads"),
                        r.getInt("download_count"),
                        r.getInt("view_count"),
                        toIso(r.getObject("expires_at")),
                        toIso(r.getObject("disabled_at")),
                        toIso(r.getObject("created_at"))),
                user);
    }

    @Transactional
    public void disable(long user, long linkId) {
        int updated = db.update(
                "UPDATE share_link SET disabled_at=UTC_TIMESTAMP(3) WHERE id=? AND creator_user_id=? AND disabled_at IS NULL",
                linkId, user);
        if (updated != 1) throw BizException.notFound("分享链接不存在或已停用");
    }

    private Map<String, Object> loadActiveLink(String token) {
        if (token == null || token.isBlank() || token.length() > 128) {
            throw BizException.notFound("分享链接无效或已失效");
        }
        String hash = sha256Hex(token.strip());
        var rows = db.queryForList(
                "SELECT sl.*, n.name node_name, n.node_type, n.hidden, n.current_version_id, "
                        + "COALESCE(b.size_bytes, 0) size_bytes, COALESCE(v.mime_type, '') mime_type, "
                        + "COALESCE(u.display_name, '') owner_name "
                        + "FROM share_link sl "
                        + "JOIN space s ON s.id=sl.space_id AND s.deleted=0 AND s.status='ACTIVE' "
                        + "JOIN file_node n ON n.id=sl.node_id AND n.deleted_at IS NULL "
                        + "LEFT JOIN file_version v ON v.id=n.current_version_id "
                        + "LEFT JOIN blob_object b ON b.id=v.blob_id "
                        + "LEFT JOIN sys_user u ON u.id=sl.creator_user_id "
                        + "WHERE sl.token_hash=?",
                hash);
        if (rows.isEmpty()) throw BizException.notFound("分享链接无效或已失效");
        var row = rows.get(0);
        if (row.get("disabled_at") != null || Boolean.TRUE.equals(row.get("hidden"))) {
            throw BizException.notFound("该分享链接已停用");
        }
        Object exp = row.get("expires_at");
        if (exp instanceof LocalDateTime ldt && ldt.isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw BizException.notFound("该分享链接已过期");
        }
        return row;
    }

    private void checkPasswordIfNeeded(Map<String, Object> link, String password) {
        String pwdHash = (String) link.get("password_hash");
        if (pwdHash == null) return;
        if (password == null || !BCRYPT.matches(password.strip(), pwdHash)) {
            throw new BizException(HttpStatus.FORBIDDEN, 403, "提取码错误或未提供提取码");
        }
    }

    public PublicShareMeta publicMeta(String token, String password) {
        var link = loadActiveLink(token);
        String pwdHash = (String) link.get("password_hash");
        boolean requiresPwd = pwdHash != null;
        boolean unlocked = !requiresPwd || (password != null && BCRYPT.matches(password.strip(), pwdHash));
        db.update("UPDATE share_link SET view_count=view_count+1 WHERE id=?", ((Number) link.get("id")).longValue());
        return new PublicShareMeta(
                String.valueOf(link.get("node_id")),
                String.valueOf(link.get("node_name")),
                String.valueOf(link.get("node_type")),
                ((Number) link.get("size_bytes")).longValue(),
                String.valueOf(link.get("mime_type")),
                String.valueOf(link.get("owner_name")),
                requiresPwd,
                unlocked,
                Boolean.TRUE.equals(link.get("allow_download")) || Integer.valueOf(1).equals(link.get("allow_download")),
                toIso(link.get("expires_at")));
    }

    public boolean verifyPassword(String token, String password) {
        var link = loadActiveLink(token);
        checkPasswordIfNeeded(link, password);
        return true;
    }

    public PublicNodeList publicNodes(String token, String password, Long parentId) {
        var link = loadActiveLink(token);
        checkPasswordIfNeeded(link, password);
        long spaceId = ((Number) link.get("space_id")).longValue();
        long rootNodeId = ((Number) link.get("node_id")).longValue();
        if (!"FOLDER".equals(link.get("node_type"))) {
            throw new BizException(HttpStatus.BAD_REQUEST, 400, "该分享为单个文件");
        }
        long targetParent = parentId == null ? rootNodeId : parentId;
        Integer inside = db.queryForObject(
                "SELECT COUNT(*) FROM file_node_closure WHERE space_id=? AND ancestor_id=? AND descendant_id=?",
                Integer.class, spaceId, rootNodeId, targetParent);
        if (inside == null || inside == 0) {
            throw BizException.notFound("目录不存在或超出分享范围");
        }
        List<PublicNode> items = db.query(
                "SELECT n.id, n.parent_id, n.name, n.node_type, COALESCE(b.size_bytes,0) size_bytes, COALESCE(v.mime_type,'') mime_type, n.updated_at "
                        + "FROM file_node n LEFT JOIN file_version v ON v.id=n.current_version_id LEFT JOIN blob_object b ON b.id=v.blob_id "
                        + "WHERE n.space_id=? AND n.parent_id=? AND n.deleted_at IS NULL AND n.hidden=FALSE ORDER BY n.node_type DESC, n.name ASC",
                (r, i) -> new PublicNode(
                        r.getString("id"),
                        r.getString("parent_id"),
                        r.getString("name"),
                        r.getString("node_type"),
                        r.getLong("size_bytes"),
                        r.getString("mime_type"),
                        toIso(r.getObject("updated_at"))),
                spaceId, targetParent);
        return new PublicNodeList(items);
    }

    @Transactional
    public DriveModels.Content publicContent(String token, String password, Long fileNodeId, boolean download) {
        var link = loadActiveLink(token);
        checkPasswordIfNeeded(link, password);
        boolean allowDownload = Boolean.TRUE.equals(link.get("allow_download")) || Integer.valueOf(1).equals(link.get("allow_download"));
        if (download && !allowDownload) {
            throw new BizException(HttpStatus.FORBIDDEN, 403, "分享者已禁止下载该资源");
        }
        Object maxDlObj = link.get("max_downloads");
        int dlCount = ((Number) link.get("download_count")).intValue();
        if (download && maxDlObj != null && dlCount >= ((Number) maxDlObj).intValue()) {
            throw new BizException(HttpStatus.FORBIDDEN, 403, "已达到该分享链接的最大下载次数");
        }

        long spaceId = ((Number) link.get("space_id")).longValue();
        long rootNodeId = ((Number) link.get("node_id")).longValue();
        long targetFileId = fileNodeId == null ? rootNodeId : fileNodeId;

        Integer inside = db.queryForObject(
                "SELECT COUNT(*) FROM file_node_closure WHERE space_id=? AND ancestor_id=? AND descendant_id=?",
                Integer.class, spaceId, rootNodeId, targetFileId);
        if (inside == null || inside == 0) {
            throw BizException.notFound("文件不存在或超出分享范围");
        }
        var rows = db.query(
                "SELECT n.name, n.node_type, n.hidden, b.storage_key, b.storage_profile_id, b.size_bytes, v.mime_type "
                        + "FROM file_node n JOIN file_version v ON v.id=n.current_version_id JOIN blob_object b ON b.id=v.blob_id "
                        + "WHERE n.space_id=? AND n.id=? AND n.deleted_at IS NULL AND n.hidden=FALSE",
                (r, i) -> {
                    long profileId = r.getObject("storage_profile_id") == null ? 1L : r.getLong("storage_profile_id");
                    Path path = storageRouter.resolveReadablePath(profileId, r.getString("storage_key"));
                    return new DriveModels.Content(path, r.getLong("size_bytes"), r.getString("name"), r.getString("mime_type"));
                },
                spaceId, targetFileId);
        if (rows.isEmpty() || !Files.isRegularFile(rows.get(0).path())) {
            throw BizException.notFound("文件内容不存在");
        }
        if (download) {
            db.update("UPDATE share_link SET download_count=download_count+1 WHERE id=?", ((Number) link.get("id")).longValue());
        }
        return rows.get(0);
    }
}
