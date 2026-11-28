package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.modules.drive.DriveModels.MediaSource;
import com.wanyue.cloud.modules.drive.storage.StorageRouterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VideoStreamingServiceTest {
    @TempDir Path temp;

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "HLS_TEST_SOURCE", matches = ".+")
    void realSourceRangeProxyPreservesTheEntireDuration() throws Exception {
        Path original = Path.of(System.getenv("HLS_TEST_SOURCE"));
        long length = Files.size(original);
        try (var header = Files.newInputStream(original)) {
            var metadata = VideoContainerInspector.mp4Probe(header.readNBytes(8 * 1024 * 1024));
            assertNotNull(metadata, "The real source moov should provide a bounded codec plan");
            assertEquals("h264", metadata.videoCodec());
            assertFalse(metadata.videoTranscodeRequired());
            assertTrue(metadata.durationSeconds() > 1300);
        }
        var source = new MediaSource(88, 9, "object", length, "source.mp4", "video/mp4", "");
        var storage = mock(StorageRouterService.class);
        when(storage.isRemote(9)).thenReturn(true);
        when(storage.openRange(eq(source), anyLong(), anyLong())).thenAnswer(call -> {
            long start = call.getArgument(1);
            long end = call.getArgument(2);
            try (var input = Files.newInputStream(original)) {
                input.skipNBytes(start);
                return new ByteArrayInputStream(input.readNBytes((int) (end - start + 1)));
            }
        });
        var assets = new PreviewAssetService(mock(org.springframework.jdbc.core.JdbcTemplate.class), temp.resolve("assets").toString(),
                "http://localhost:1", "ffmpeg", "ffprobe", 60, 1, 1024*1024);
        double duration = assets.probeVideo(new com.wanyue.cloud.modules.drive.DriveModels.Content(original, length, "source.mp4", "video/mp4")).durationSeconds();
        var service = new VideoStreamingService(storage, assets, temp.toString(), "ffmpeg", 1, 120);
        try {
            service.getOrStart(source, false);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
            VideoStreamingService.State state;
            do {
                state = service.getOrStart(source, false);
                if ("READY".equals(state.status()) || "FAILED".equals(state.status())) break;
                Thread.sleep(100);
            } while (System.nanoTime() < deadline);
            assertEquals("READY", state.status(), state.errorMessage());
            assertEquals(duration, state.availableSeconds(), 3);
        } finally { service.close(); assets.shutdown(); }
    }

    @Test void exposesRealPlayableSegmentsBeforeTheRemoteFileHasFinishedDownloading() throws Exception {
        Path original = temp.resolve("source.mp4");
        Process fixture = new ProcessBuilder("ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i",
                "testsrc2=size=320x180:rate=24", "-t", "60", "-c:v", "libx264", "-preset", "ultrafast",
                "-crf", "16", "-g", "48", "-pix_fmt", "yuv444p", "-movflags", "+frag_keyframe+empty_moov", original.toString())
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        assertTrue(fixture.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, fixture.exitValue());
        byte[] bytes = Files.readAllBytes(original);
        AtomicLong delivered = new AtomicLong();
        var storage = mock(StorageRouterService.class);
        var assets = new PreviewAssetService(mock(org.springframework.jdbc.core.JdbcTemplate.class), temp.resolve("assets").toString(),
                "http://localhost:1", "ffmpeg", "ffprobe", 60, 1, 1024*1024);
        var source = new MediaSource(44, 9, "object", bytes.length, "source.mp4", "video/mp4", "");
        when(storage.isRemote(9)).thenReturn(true);
        when(storage.openRange(eq(source), anyLong(), anyLong())).thenAnswer(call -> {
            int from = ((Long) call.getArgument(1)).intValue();
            int length = (int) ((Long) call.getArgument(2) - from + 1);
            return new java.io.FilterInputStream(new ByteArrayInputStream(bytes, from, length)) {
                @Override public int read(byte[] buffer, int offset, int count) throws java.io.IOException {
                    try { Thread.sleep(25); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new java.io.InterruptedIOException(); }
                    int read = super.read(buffer, offset, Math.min(count, 16384));
                    if (read > 0) delivered.addAndGet(read);
                    return read;
                }
            };
        });
        var service = new VideoStreamingService(storage, assets, temp.toString(), "ffmpeg", 1, 120);
        try {
            service.getOrStart(source, false);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            VideoStreamingService.State state;
            do {
                state = service.getOrStart(source, false);
                assertNotEquals("FAILED", state.status(), state.errorMessage());
                if ("PLAYABLE".equals(state.status())) break;
                Thread.sleep(100);
            } while (System.nanoTime() < deadline);
            assertEquals("PLAYABLE", state.status());
            assertTrue(state.availableSeconds() >= 4);
            assertTrue(delivered.get() < bytes.length, "Playback must not wait for a complete source download");
            String manifest = Files.readString(service.file(44, "index.m3u8"));
            assertFalse(manifest.contains("#EXT-X-ENDLIST"), "Producer must still be encoding");
            Path segment = service.thumbnailInput(44).orElseThrow();
            var decode = new ProcessBuilder("ffmpeg", "-v", "error", "-i", segment.toString(), "-frames:v", "1", "-f", "null", "-")
                    .redirectError(ProcessBuilder.Redirect.INHERIT).start();
            assertTrue(decode.waitFor(10, TimeUnit.SECONDS));
            assertEquals(0, decode.exitValue(), "Published segments must contain a decodable video frame");
            verify(storage, never()).resolveReadablePath(anyLong(), anyString());
            assertThrows(com.wanyue.cloud.common.exception.BizException.class, () -> service.file(44, "../source.mp4"));
        } finally { service.close(); assets.shutdown(); }
    }

    @Test void completedCacheSurvivesRestartAndMissingSegmentsInvalidateIt() throws Exception {
        Path dir = temp.resolve("hls/55");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("segment-000000.ts"), "segment");
        Files.writeString(dir.resolve("index.m3u8"), "#EXTM3U\n#EXTINF:4,\nsegment-000000.ts\n#EXT-X-ENDLIST\n");
        Files.writeString(dir.resolve("complete"), "complete");
        var storage = mock(StorageRouterService.class);
        var service = new VideoStreamingService(storage, mock(PreviewAssetService.class), temp.toString(), "ffmpeg", 1, 60);
        try {
            assertTrue(service.hasPlayable(55));
            assertEquals("READY", service.status(55).status());
            Files.delete(dir.resolve("segment-000000.ts"));
            assertFalse(service.hasPlayable(55));
            assertEquals("FAILED", service.status(55).status());
            verifyNoInteractions(storage);
        } finally { service.close(); }
    }

    @Test void ordinaryMp4WithAudioIsProcessedInFullThroughTheRangeProxy() throws Exception {
        Path original = temp.resolve("ordinary.mp4");
        Process fixture = new ProcessBuilder("ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i",
                "testsrc2=size=640x360:rate=30", "-f", "lavfi", "-i", "sine=frequency=440", "-t", "16",
                "-c:v", "libx264", "-preset", "ultrafast", "-b:v", "4M", "-c:a", "aac", "-movflags", "+faststart", original.toString())
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        assertTrue(fixture.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, fixture.exitValue());
        byte[] bytes = Files.readAllBytes(original);
        var storage = mock(StorageRouterService.class);
        var source = new MediaSource(66, 9, "object", bytes.length, "ordinary.mp4", "video/mp4", "");
        when(storage.isRemote(9)).thenReturn(true);
        when(storage.openRange(eq(source), anyLong(), anyLong())).thenAnswer(call -> {
            int start = ((Long) call.getArgument(1)).intValue();
            int length = (int) ((Long) call.getArgument(2) - start + 1);
            return new ByteArrayInputStream(bytes, start, length);
        });
        try (var proxy = new StreamingInputProxy(storage, source, temp.resolve("verify-input"))) {
            var client = java.net.http.HttpClient.newHttpClient();
            var response = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(proxy.url()))
                    .header("Range", "bytes=1285631-2345678").build(), java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(206, response.statusCode());
            assertArrayEquals(java.util.Arrays.copyOfRange(bytes, 1285631, 2345679), response.body());
        }
        var assets = new PreviewAssetService(mock(org.springframework.jdbc.core.JdbcTemplate.class), temp.resolve("assets").toString(),
                "http://localhost:1", "ffmpeg", "ffprobe", 60, 1, 1024*1024);
        var service = new VideoStreamingService(storage, assets, temp.toString(), "ffmpeg", 1, 120);
        try {
            service.getOrStart(source, false);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            VideoStreamingService.State state;
            do {
                state = service.getOrStart(source, false);
                if ("READY".equals(state.status()) || "FAILED".equals(state.status())) break;
                Thread.sleep(100);
            } while (System.nanoTime() < deadline);
            assertEquals("READY", state.status(), state.errorMessage());
            assertEquals(16, state.availableSeconds(), .2);
        } finally { service.close(); assets.shutdown(); }
    }
}
