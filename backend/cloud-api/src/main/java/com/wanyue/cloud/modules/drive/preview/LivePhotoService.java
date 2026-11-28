package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.drive.DriveService;
import com.wanyue.cloud.modules.drive.DriveModels;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.io.IOException;

@Service
public class LivePhotoService {
    private final DriveService drive;
    private final JdbcTemplate db;
    private final PreviewAssetService assets;
    public record State(boolean livePhoto, String status, String errorMessage) {}

    public LivePhotoService(DriveService drive, JdbcTemplate db, PreviewAssetService assets) {
        this.drive = drive;
        this.db = db;
        this.assets = assets;
    }

    public State inspect(long user, long space, long node, Long version, boolean retry) throws IOException {
        var photo = drive.mediaSource(user, space, node, version);
        if (!"IMAGE".equals(MediaTimelineService.detectMediaType(photo.name(), photo.mimeType()))) return new State(false, "NONE", null);
        var pairs = db.queryForList("SELECT video_node_id, video_version_id FROM live_photo_pair WHERE photo_version_id=?", photo.versionId());
        var source = photo;
        if (!pairs.isEmpty()) {
            var pair = pairs.get(0);
            // A linked video has its own permissions and lifecycle. Never authorize it through the photo alone.
            source = drive.mediaSource(user, space, ((Number) pair.get("video_node_id")).longValue(), ((Number) pair.get("video_version_id")).longValue());
        }
        var existing = assets.findAsset(photo.versionId(), "LIVE_PHOTO_MP4");
        if (pairs.isEmpty() && existing.isEmpty()) {
            if (photo.size() > 64L * 1024 * 1024 || !photo.name().toLowerCase(java.util.Locale.ROOT).matches(".*\\.jpe?g$")) return new State(false, "NONE", null);
            if (MotionPhotoReader.find(drive.materializeMediaSource(photo).path()).isEmpty()) return new State(false, "NONE", null);
        }
        var input = source;
        var asset = retry
                ? assets.retryAsset(node, photo.versionId(), "LIVE_PHOTO_MP4", input.size(), () -> drive.materializeMediaSource(input))
                : assets.getOrTriggerAsset(node, photo.versionId(), "LIVE_PHOTO_MP4", input.size(), () -> drive.materializeMediaSource(input));
        return new State(true, asset.status(), asset.errorMessage());
    }

    public State pair(long user, long space, long node, long version, long videoNode) throws IOException {
        return pair(user, space, node, version, videoNode, null);
    }

    public State pair(long user, long space, long node, long version, long videoNode, Long posterNode) throws IOException {
        drive.resolveNodeAccess(user, space, node, true, false);
        var photo = drive.mediaSource(user, space, node, version);
        var video = drive.mediaSource(user, space, videoNode, null);
        drive.resolveNodeAccess(user, space, videoNode, true, false);
        if (!"IMAGE".equals(MediaTimelineService.detectMediaType(photo.name(), photo.mimeType()))
                || !"VIDEO".equals(MediaTimelineService.detectMediaType(video.name(), video.mimeType()))) throw BizException.conflict("请选择图片和配套视频");
        if (video.size() <= 0 || video.size() > 64L * 1024 * 1024) throw BizException.conflict("配套视频须小于 64 MB");
        DriveModels.MediaSource poster = null;
        if (posterNode != null) {
            drive.resolveNodeAccess(user, space, posterNode, true, false);
            poster = drive.mediaSource(user, space, posterNode, null);
            if (!"image/jpeg".equalsIgnoreCase(poster.mimeType()) || poster.size() > 16L * 1024 * 1024) throw BizException.conflict("实况预览图须为小于 16 MB 的 JPEG");
        }
        if (assets.findAsset(photo.versionId(), "LIVE_PHOTO_MP4").isPresent()
                && db.queryForList("SELECT photo_version_id FROM live_photo_pair WHERE photo_version_id=?", photo.versionId()).isEmpty()) {
            throw BizException.conflict("此照片已有内嵌实况视频");
        }
        // Immutable binding prevents a late conversion from overwriting a newer pairing.
        int inserted = db.update("INSERT IGNORE INTO live_photo_pair(photo_version_id, video_node_id, video_version_id, poster_node_id, poster_version_id) VALUES (?, ?, ?, ?, ?)",
                photo.versionId(), videoNode, video.versionId(), posterNode, poster == null ? null : poster.versionId());
        if (inserted == 0) {
            var rows = db.queryForList("SELECT video_node_id, video_version_id FROM live_photo_pair WHERE photo_version_id=?", photo.versionId());
            var row = rows.get(0);
            if (((Number) row.get("video_node_id")).longValue() != videoNode || ((Number) row.get("video_version_id")).longValue() != video.versionId()) throw BizException.conflict("此照片版本已绑定实况视频");
        }
        return inspect(user, space, node, version, true);
    }

    public record Part(DriveModels.Content content, long offset, long length, String name, String mimeType) {}

    /** Return original tracks, never the lossy browser derivative, for native Photos import. */
    public Part part(long user, long space, long node, Long version, String kind) throws IOException {
        var photo = drive.mediaSource(user, space, node, version);
        if (!java.util.Set.of("photo", "video", "poster").contains(kind)) throw BizException.notFound("实况资源不存在");
        if (!"IMAGE".equals(MediaTimelineService.detectMediaType(photo.name(), photo.mimeType()))) throw BizException.notFound("文件不是照片");
        var rows = db.queryForList("SELECT video_node_id, video_version_id, poster_node_id, poster_version_id FROM live_photo_pair WHERE photo_version_id=?", photo.versionId());
        if (!rows.isEmpty()) {
            var row = rows.get(0);
            var video = drive.mediaSource(user, space, ((Number) row.get("video_node_id")).longValue(), ((Number) row.get("video_version_id")).longValue());
            var source = "video".equals(kind) ? video : photo;
            if ("poster".equals(kind) && row.get("poster_node_id") != null) {
                source = drive.mediaSource(user, space, ((Number) row.get("poster_node_id")).longValue(), ((Number) row.get("poster_version_id")).longValue());
            }
            var content = drive.materializeMediaSource(source);
            return new Part(content, 0, content.size(), content.name(), content.mimeType());
        }
        if (photo.size() > 64L * 1024 * 1024) throw BizException.conflict("动态照片超出解析上限");
        var content = drive.materializeMediaSource(photo);
        var segment = MotionPhotoReader.find(content.path()).orElseThrow(() -> BizException.notFound("未找到实况视频"));
        return "video".equals(kind)
                ? new Part(content, segment.offset(), segment.length(), "motion.mp4", "video/mp4")
                : new Part(content, 0, segment.offset(), "photo.jpg", "image/jpeg");
    }

    public java.util.Optional<DriveModels.Content> posterSource(long user, long space, long node, long version) throws IOException {
        var rows = db.queryForList("SELECT poster_node_id FROM live_photo_pair WHERE photo_version_id=?", version);
        if (rows.isEmpty() || rows.get(0).get("poster_node_id") == null) return java.util.Optional.empty();
        return java.util.Optional.of(part(user, space, node, version, "poster").content());
    }

    public record MotionExport(Part photo, java.nio.file.Path video) {}

    public MotionExport motionExport(long user, long space, long node, Long version) throws IOException {
        var source = drive.mediaSource(user, space, node, version);
        // Same authorization for both originals as playback and native iOS import.
        var photo = part(user, space, node, source.versionId(), "poster");
        if (!"image/jpeg".equalsIgnoreCase(photo.mimeType())) throw BizException.conflict("此照片暂无 JPEG 预览图，无法导出安卓动态照片");
        var state = inspect(user, space, node, source.versionId(), false);
        if (!"READY".equals(state.status())) throw BizException.conflict("实况视频尚未就绪，请待处理完成后下载");
        return new MotionExport(photo, assets.resolveReadyAssetPath(source.versionId(), "LIVE_PHOTO_MP4"));
    }

    public void authorizePair(long user, long space, long node, long version) {
        drive.mediaSource(user, space, node, version);
        var pairs = db.queryForList("SELECT video_node_id, video_version_id FROM live_photo_pair WHERE photo_version_id=?", version);
        if (!pairs.isEmpty()) {
            var pair = pairs.get(0);
            drive.mediaSource(user, space, ((Number) pair.get("video_node_id")).longValue(), ((Number) pair.get("video_version_id")).longValue());
        }
    }

    /** The short-lived ticket was issued after checking both nodes, including the vault header.
     * Native video requests cannot resend that header; preserve the ticket's grant until expiry.
     * Still check that neither original has been trashed or purged. */
    public void validateTicketSources(long space, long node, long version) {
        drive.mediaSourceByVerifiedTicket(space, node, version);
        var pairs = db.queryForList("SELECT video_node_id, video_version_id FROM live_photo_pair WHERE photo_version_id=?", version);
        if (!pairs.isEmpty()) {
            var pair = pairs.get(0);
            drive.mediaSourceByVerifiedTicket(space, ((Number) pair.get("video_node_id")).longValue(), ((Number) pair.get("video_version_id")).longValue());
        }
    }
}
