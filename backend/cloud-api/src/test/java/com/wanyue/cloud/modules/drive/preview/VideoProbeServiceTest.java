package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.modules.drive.DriveModels;
import com.wanyue.cloud.modules.drive.storage.StorageRouterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VideoProbeServiceTest {
    @TempDir Path temp;
    @Test void slowProbeIsNonblockingAndDuplicateRequestsShareOneResult() throws Exception {
        var storage = mock(StorageRouterService.class);
        var assets = mock(PreviewAssetService.class);
        var source = new DriveModels.MediaSource(4, 9, "key", 3L << 30, "large.mp4", "video/mp4", "");
        var started = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        when(storage.probeInput(source)).thenReturn("https://storage.invalid/signed");
        var ready = PreviewAssetService.decideVideoStrategy("mp4", "h264", "aac", 2000, 1920, 1080, 1);
        when(assets.probeInput(anyString(), anyString())).thenAnswer(call -> { started.countDown(); assertTrue(finish.await(5, TimeUnit.SECONDS)); return ready; });
        var service = new VideoProbeService(storage, assets);
        try {
            assertTrue(java.util.Set.of("PENDING", "PROCESSING").contains(service.getOrRequest(source).status()));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertEquals("PROCESSING", service.getOrRequest(source).status());
            finish.countDown();
            for (int i = 0; i < 100 && !"READY".equals(service.getOrRequest(source).status()); i++) Thread.sleep(10);
            assertEquals(ready, service.getOrRequest(source));
            verify(assets, times(1)).probeInput(anyString(), anyString());
            verify(storage, never()).resolveReadablePath(anyLong(), anyString());
        } finally { finish.countDown(); service.close(); }
    }

    @Test void missingProbeNeverPretendsCodecAndDurationAreKnown() throws Exception {
        var service = new PreviewAssetService(mock(org.springframework.jdbc.core.JdbcTemplate.class), temp.toString(), "http://127.0.0.1:1",
                "missing-ffmpeg", temp.resolve("missing-ffprobe").toString(), 5, 1, 209715200);
        try {
            var result = service.probeInput(temp.resolve("video.mp4").toString(), "video.mp4");
            assertEquals("FAILED", result.status());
            assertEquals("UNKNOWN", result.strategy());
            assertEquals("", result.videoCodec());
        } finally { service.shutdown(); }
    }
}
