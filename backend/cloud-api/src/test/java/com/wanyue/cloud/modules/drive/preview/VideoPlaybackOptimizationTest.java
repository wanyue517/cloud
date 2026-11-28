package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.modules.drive.DriveModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VideoPlaybackOptimizationTest {
    @TempDir Path temp;

    private static byte[] box(String name, byte[] body) {
        return ByteBuffer.allocate(8 + body.length).putInt(8 + body.length)
                .put(name.getBytes(StandardCharsets.US_ASCII)).put(body).array();
    }

    @Test void detectsFragmentationWithoutMatchingStringsInsideMedia() {
        assertTrue(VideoContainerInspector.fragmented(box("moov", box("mvex", new byte[0]))));
        assertTrue(VideoContainerInspector.fragmented(box("moof", new byte[0])));
        assertFalse(VideoContainerInspector.fragmented(box("mdat", "moofmvex".getBytes(StandardCharsets.US_ASCII))));
        assertFalse(VideoContainerInspector.fragmented(new byte[]{0,0,0,1,109,111,111,118}));
    }

    @Test void preservesCompatibleTracksIndependently() {
        var compatible = PreviewAssetService.decideVideoStrategy("mp4", "h264", "aac", 3, 128, 72, 1000);
        assertEquals(List.of("-map", "0:V:0", "-map", "0:a:0?", "-c:v", "copy", "-c:a", "copy", "-movflags", "+faststart"),
                PreviewAssetService.compatibilityArguments(compatible));
        var audioOnly = PreviewAssetService.decideVideoStrategy("matroska", "h264", "pcm_s16le", 3, 128, 72, 1000);
        var args = PreviewAssetService.compatibilityArguments(audioOnly);
        assertEquals("copy", args.get(args.indexOf("-c:v") + 1));
        assertEquals("aac", args.get(args.indexOf("-c:a") + 1));
        var hevc = PreviewAssetService.decideVideoStrategy("mp4", "hevc", "aac", 3, 128, 72, 1000);
        assertTrue(PreviewAssetService.compatibilityArguments(hevc).contains("libx264"));
    }

    @Test void realFragmentedMp4BecomesFaststartWithoutReencoding() throws Exception {
        Path input = temp.resolve("fragmented.mp4");
        var command = List.of("ffmpeg", "-y", "-v", "error", "-f", "lavfi", "-i", "testsrc2=size=128x72:rate=30",
                "-f", "lavfi", "-i", "sine=frequency=440", "-t", "2", "-c:v", "libx264", "-c:a", "aac",
                "-movflags", "+frag_keyframe+empty_moov", input.toString());
        Process fixture = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        assertTrue(fixture.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, fixture.exitValue());
        assertTrue(VideoContainerInspector.fragmented(Files.readAllBytes(input)));
        var db = mock(JdbcTemplate.class);
        var assets = new PreviewAssetService(db, temp.resolve("previews").toString(), "http://localhost:1", "ffmpeg", "ffprobe", 60, 1, 1024*1024);
        try {
            var probe = assets.probeVideo(new DriveModels.Content(input, Files.size(input), "fragmented.mp4", "video/mp4"));
            assertEquals("copy", PreviewAssetService.compatibilityArguments(probe).get(5));
            var method = PreviewAssetService.class.getDeclaredMethod("transcodeVideoViaFfmpeg", String.class, long.class, DriveModels.Content.class);
            method.setAccessible(true);
            method.invoke(assets, "1", 2L, new DriveModels.Content(input, Files.size(input), "fragmented.mp4", "video/mp4"));
            Path output = temp.resolve("previews/video/2.mp4");
            byte[] bytes = Files.readAllBytes(output);
            assertFalse(VideoContainerInspector.fragmented(bytes));
            String binary = new String(bytes, StandardCharsets.ISO_8859_1);
            assertTrue(binary.indexOf("moov") < binary.indexOf("mdat"));
            assertEquals("h264", assets.probeVideo(new DriveModels.Content(output, bytes.length, "output.mp4", "video/mp4")).videoCodec());
            verify(db).update(contains("status='READY'"), eq("video/2.mp4"), eq("video/mp4"), eq((long) bytes.length), eq(1L));
        } finally { assets.shutdown(); }
    }
}
