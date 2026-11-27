package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.drive.DriveService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MediaTimelineService {
    private static final Pattern EXIF_DATE_PATTERN = Pattern.compile("(20\\d{2}|19\\d{2}):(0[1-9]|1[0-2]):(0[1-9]|[12]\\d|3[01]) ([01]\\d|2[0-3]):([0-5]\\d):([0-5]\\d)");
    private static final DateTimeFormatter EXIF_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
    private static final Set<String> IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif");
    private static final Set<String> VIDEO_EXTS = Set.of("mp4", "mov", "mkv", "webm", "avi", "m4v", "3gp");

    private final JdbcTemplate db;
    private final DriveService drive;

    public MediaTimelineService(JdbcTemplate db, DriveService drive) {
        this.db = db;
        this.drive = drive;
    }

    public record TimelineItem(
            String id,
            String spaceId,
            String spaceName,
            String parentId,
            String name,
            long size,
            String mimeType,
            String mediaType,
            String takenAt,
            String dateGroup,
            Integer width,
            Integer height,
            Integer durationSeconds,
            String currentVersionId,
            long revision) {}

    public record TimelinePage(
            List<TimelineItem> items,
            long total,
            int page,
            int pageSize,
            boolean hasMore) {}

    public static String detectMediaType(String name, String mimeType) {
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (mime.startsWith("image/")) return "IMAGE";
        if (mime.startsWith("video/")) return "VIDEO";
        String ext = extractExt(name);
        if (IMAGE_EXTS.contains(ext)) return "IMAGE";
        if (VIDEO_EXTS.contains(ext)) return "VIDEO";
        return null;
    }

    private static String extractExt(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    /** 异步或上传时解析媒体 EXIF 拍摄时间与分辨率并写入 media_metadata。 */
    public void extractAndUpsertMetadata(long spaceId, long nodeId, long versionId, String name, String mimeType, Path filePath) {
        String mediaType = detectMediaType(name, mimeType);
        if (mediaType == null) return;
        Integer width = null;
        Integer height = null;
        LocalDateTime takenAt = null;
        if ("IMAGE".equals(mediaType) && filePath != null && Files.isRegularFile(filePath)) {
            try {
                BufferedImage img = ImageIO.read(filePath.toFile());
                if (img != null) {
                    width = img.getWidth();
                    height = img.getHeight();
                }
            } catch (Exception ignored) {
                // ignore unreadable image formats
            }
            takenAt = tryExtractExifTakenAt(filePath);
        }
        db.update(
                "INSERT INTO media_metadata(version_id, node_id, space_id, media_type, taken_at, width, height, duration_seconds, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, NULL, UTC_TIMESTAMP(3)) "
                        + "ON DUPLICATE KEY UPDATE taken_at=COALESCE(VALUES(taken_at), taken_at), "
                        + "width=COALESCE(VALUES(width), width), height=COALESCE(VALUES(height), height), updated_at=UTC_TIMESTAMP(3)",
                versionId, nodeId, spaceId, mediaType, takenAt, width, height);
    }

    private static LocalDateTime tryExtractExifTakenAt(Path filePath) {
        try (var in = Files.newInputStream(filePath)) {
            byte[] header = in.readNBytes(65536);
            String ascii = new String(header, StandardCharsets.ISO_8859_1);
            Matcher m = EXIF_DATE_PATTERN.matcher(ascii);
            if (m.find()) {
                return LocalDateTime.parse(m.group(0), EXIF_FORMATTER);
            }
        } catch (Exception ignored) {
            // fallback to node created_at
        }
        return null;
    }

    /**
     * 云端相册时光轴分页查询：
     * - 按拍摄时间（EXIF taken_at 优先，兜底节点创建时间）倒序排列
     * - 严格排除 PRIVATE 私密空间、隐藏文件（hidden=TRUE）、回收站文件及无权/失权共享项
     */
    public TimelinePage listTimeline(long user, Long spaceId, String mediaTypeFilter, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(200, pageSize));
        String typeFilter = mediaTypeFilter == null ? "ALL" : mediaTypeFilter.strip().toUpperCase(Locale.ROOT);

        StringBuilder sql = new StringBuilder(
                "SELECT n.id, n.space_id, s.name space_name, n.parent_id, n.name, n.current_version_id, n.revision, "
                        + "COALESCE(b.size_bytes, 0) size_bytes, COALESCE(v.mime_type, '') mime_type, "
                        + "COALESCE(m.taken_at, n.created_at) sort_time, m.width, m.height, m.duration_seconds, m.media_type "
                        + "FROM file_node n "
                        + "JOIN space s ON s.id=n.space_id AND s.deleted=0 AND s.status='ACTIVE' AND s.kind IN ('PERSONAL','SHARED') "
                        + "LEFT JOIN space_member sm ON sm.space_id=s.id AND sm.user_id=? AND sm.deleted=0 AND (sm.expires_at IS NULL OR sm.expires_at>UTC_TIMESTAMP(3)) "
                        + "LEFT JOIN file_version v ON v.id=n.current_version_id "
                        + "LEFT JOIN blob_object b ON b.id=v.blob_id "
                        + "LEFT JOIN media_metadata m ON m.version_id=n.current_version_id "
                        + "WHERE n.node_type='FILE' AND n.deleted_at IS NULL AND n.hidden=FALSE "
                        + "AND (s.owner_user_id=? OR sm.id IS NOT NULL) ");
        List<Object> args = new ArrayList<>();
        args.add(user);
        args.add(user);
        if (spaceId != null && spaceId > 0) {
            sql.append("AND n.space_id=? ");
            args.add(spaceId);
        }
        sql.append("AND (v.mime_type LIKE 'image/%' OR v.mime_type LIKE 'video/%' OR m.media_type IN ('IMAGE','VIDEO') "
                + "OR LOWER(n.name) REGEXP '\\\\.(jpg|jpeg|png|webp|gif|bmp|heic|heif|avif|mp4|mov|mkv|webm|avi|m4v)$') ");
        sql.append("ORDER BY sort_time DESC, n.id DESC LIMIT 2000");

        var rows = db.queryForList(sql.toString(), args.toArray());
        List<TimelineItem> authorized = new ArrayList<>();
        for (var r : rows) {
            long sid = ((Number) r.get("space_id")).longValue();
            long nid = ((Number) r.get("id")).longValue();
            String name = String.valueOf(r.get("name"));
            String mime = String.valueOf(r.get("mime_type"));
            String detectedType = r.get("media_type") != null ? String.valueOf(r.get("media_type")) : detectMediaType(name, mime);
            if (detectedType == null) continue;
            if ("IMAGE".equals(typeFilter) && !"IMAGE".equals(detectedType)) continue;
            if ("VIDEO".equals(typeFilter) && !"VIDEO".equals(detectedType)) continue;
            try {
                drive.resolveNodeAccess(user, sid, nid, false, false);
            } catch (BizException ex) {
                continue;
            }
            Instant instant = r.get("sort_time") instanceof LocalDateTime ldt
                    ? ldt.toInstant(ZoneOffset.UTC)
                    : Instant.now();
            String iso = instant.toString();
            String dateGroup = iso.length() >= 10 ? iso.substring(0, 10) : "1970-01-01";
            authorized.add(new TimelineItem(
                    String.valueOf(nid),
                    String.valueOf(sid),
                    String.valueOf(r.get("space_name")),
                    String.valueOf(r.get("parent_id")),
                    name,
                    ((Number) r.get("size_bytes")).longValue(),
                    mime,
                    detectedType,
                    iso,
                    dateGroup,
                    r.get("width") == null ? null : ((Number) r.get("width")).intValue(),
                    r.get("height") == null ? null : ((Number) r.get("height")).intValue(),
                    r.get("duration_seconds") == null ? null : ((Number) r.get("duration_seconds")).intValue(),
                    r.get("current_version_id") == null ? null : String.valueOf(r.get("current_version_id")),
                    ((Number) r.get("revision")).longValue()));
        }

        long total = authorized.size();
        int from = Math.min(authorized.size(), (safePage - 1) * safeSize);
        int to = Math.min(authorized.size(), from + safeSize);
        List<TimelineItem> slice = authorized.subList(from, to);
        return new TimelinePage(slice, total, safePage, safeSize, to < authorized.size());
    }
}
