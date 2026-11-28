package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.drive.DriveModels;
import com.wanyue.cloud.modules.drive.DriveService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LivePhotoServiceTest {
    final DriveService drive = mock(DriveService.class);
    final JdbcTemplate db = mock(JdbcTemplate.class);
    final PreviewAssetService assets = mock(PreviewAssetService.class);
    final LivePhotoService service = new LivePhotoService(drive, db, assets);
    final DriveModels.MediaSource photo = new DriveModels.MediaSource(4, 1, "photo", 100, "photo.jpg", "image/jpeg", "");
    final DriveModels.MediaSource video = new DriveModels.MediaSource(8, 1, "video", 100, "photo.mov", "video/quicktime", "");

    @Test void pairedVideoRequiresItsOwnAccessEvenWhenPreviewAlreadyExists() {
        when(drive.mediaSource(1, 2, 3, 4L)).thenReturn(photo);
        when(db.queryForList(anyString(), eq(4L))).thenReturn(List.of(Map.of("video_node_id", 7L, "video_version_id", 8L)));
        when(drive.mediaSource(1, 2, 7, 8L)).thenThrow(BizException.notFound("无权访问视频"));
        assertThrows(BizException.class, () -> service.inspect(1, 2, 3, 4L, false));
        verifyNoInteractions(assets);
    }

    @Test void pairingRequiresWriteAccessBeforeChangingAnyBinding() {
        doThrow(BizException.conflict("只读")).when(drive).resolveNodeAccess(1, 2, 3, true, false);
        assertThrows(BizException.class, () -> service.pair(1, 2, 3, 4, 7));
        verifyNoInteractions(db, assets);
    }

    @Test void derivativeUsesPhotoVersionAndImmutableVideoVersion() throws Exception {
        when(drive.mediaSource(1, 2, 3, 4L)).thenReturn(photo);
        when(drive.mediaSource(1, 2, 7, 8L)).thenReturn(video);
        when(db.queryForList(anyString(), eq(4L))).thenReturn(List.of(Map.of("video_node_id", 7L, "video_version_id", 8L)));
        when(assets.getOrTriggerAsset(eq(3L), eq(4L), eq("LIVE_PHOTO_MP4"), eq(100L), any()))
                .thenReturn(new PreviewModels.PreviewAsset("9", "3", "4", "LIVE_PHOTO_MP4", "PENDING", null, 0, null, 1, ""));
        assertEquals("PENDING", service.inspect(1, 2, 3, 4L, false).status());
        verify(drive).mediaSource(1, 2, 7, 8L);
        verify(drive, never()).materializeMediaSource(any());
    }

    @Test void ticketValidationChecksBothOriginalsWithoutNeedingVaultHeader() {
        when(db.queryForList(anyString(), eq(4L))).thenReturn(List.of(Map.of("video_node_id", 7L, "video_version_id", 8L)));
        service.validateTicketSources(2, 3, 4);
        verify(drive).mediaSourceByVerifiedTicket(2, 3, 4);
        verify(drive).mediaSourceByVerifiedTicket(2, 7, 8);
        verify(drive, never()).mediaSource(anyLong(), anyLong(), anyLong(), any());
    }
}
