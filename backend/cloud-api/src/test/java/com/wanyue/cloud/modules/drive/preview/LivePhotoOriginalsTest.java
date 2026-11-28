package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.drive.DriveModels;
import com.wanyue.cloud.modules.drive.DriveService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LivePhotoOriginalsTest {
    @TempDir Path directory;
    final DriveService drive = mock(DriveService.class);
    final JdbcTemplate db = mock(JdbcTemplate.class);
    final PreviewAssetService assets = mock(PreviewAssetService.class);
    final LivePhotoService service = new LivePhotoService(drive, db, assets);
    final DriveModels.MediaSource photo = new DriveModels.MediaSource(4, 1, "original.heic", 10, "photo.heic", "image/heic", "");
    final DriveModels.MediaSource video = new DriveModels.MediaSource(8, 1, "original.mov", 10, "photo.mov", "video/quicktime", "");
    final DriveModels.MediaSource poster = new DriveModels.MediaSource(10, 1, "poster.jpg", 10, "poster.jpg", "image/jpeg", "");

    void pair() {
        when(drive.mediaSource(1, 2, 3, 4L)).thenReturn(photo);
        when(drive.mediaSource(1, 2, 7, 8L)).thenReturn(video);
        when(drive.mediaSource(1, 2, 9, 10L)).thenReturn(poster);
        when(db.queryForList(anyString(), eq(4L))).thenReturn(List.of(Map.of("video_node_id", 7L, "video_version_id", 8L, "poster_node_id", 9L, "poster_version_id", 10L)));
    }

    @Test void iosImportDownloadsOriginalMovRatherThanTranscodedBrowserVideo() throws Exception {
        pair();
        var original = new DriveModels.Content(directory.resolve("original.mov"), 10, "photo.mov", "video/quicktime");
        when(drive.materializeMediaSource(video)).thenReturn(original);
        var part = service.part(1, 2, 3, 4L, "video");
        assertEquals(original.path(), part.content().path());
        assertEquals("video/quicktime", part.mimeType());
        verifyNoInteractions(assets);
    }

    @Test void heicPosterUsesItsOwnPinnedVersionAndReadPermission() throws Exception {
        pair();
        var content = new DriveModels.Content(directory.resolve("poster.jpg"), 10, "poster.jpg", "image/jpeg");
        when(drive.materializeMediaSource(poster)).thenReturn(content);
        assertEquals(content, service.part(1, 2, 3, 4L, "poster").content());
        verify(drive).mediaSource(1, 2, 9, 10L);
    }

    @Test void photoAccessCannotBypassMissingVideoPermission() {
        pair();
        when(drive.mediaSource(1, 2, 7, 8L)).thenThrow(BizException.notFound("视频已删除或无权访问"));
        assertThrows(BizException.class, () -> service.part(1, 2, 3, 4L, "photo"));
        verify(drive, never()).materializeMediaSource(any());
    }

    @Test void motionExportCannotStartBeforeTheCompatibleVideoIsReady() {
        pair();
        when(drive.materializeMediaSource(poster)).thenReturn(new DriveModels.Content(directory.resolve("poster.jpg"), 10, "poster.jpg", "image/jpeg"));
        when(assets.getOrTriggerAsset(eq(3L), eq(4L), eq("LIVE_PHOTO_MP4"), eq(10L), any()))
            .thenReturn(new PreviewModels.PreviewAsset("11", "3", "4", "LIVE_PHOTO_MP4", "PROCESSING", null, 0, null, 1, ""));
        assertThrows(BizException.class, () -> service.motionExport(1, 2, 3, 4L));
        verify(assets, never()).resolveReadyAssetPath(anyLong(), anyString());
    }
}
