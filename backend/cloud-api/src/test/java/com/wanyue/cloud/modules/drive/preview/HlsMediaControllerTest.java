package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import com.wanyue.cloud.modules.drive.DriveService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.*;
import java.util.Base64;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HlsMediaControllerTest {
    @TempDir Path temp;
    @Test void everyManifestAndSegmentRequiresTheCorrectTicketAndChecksTheSource() throws Exception {
        var jwt = new JwtTokenProvider(Base64.getEncoder().encodeToString("test-only-secret-at-least-32-bytes".getBytes()), "cloudlane", 900, new tools.jackson.databind.json.JsonMapper());
        var drive = mock(DriveService.class);
        var streaming = mock(VideoStreamingService.class);
        when(streaming.status(4)).thenReturn(new VideoStreamingService.State("PLAYABLE", 4, null));
        Path playlist = temp.resolve("index.m3u8");
        Files.writeString(playlist, "#EXTM3U\n#EXTINF:4,\nsegment-000000.ts\n");
        Path segment = temp.resolve("segment-000000.ts");
        Files.write(segment, new byte[]{1,2,3});
        when(streaming.file(4, "index.m3u8")).thenReturn(playlist);
        when(streaming.file(4, "segment-000000.ts")).thenReturn(segment);
        var mvc = MockMvcBuilders.standaloneSetup(new HlsMediaController(jwt, drive, streaming))
                .setControllerAdvice(new com.wanyue.cloud.common.exception.GlobalExceptionHandler(mock(com.wanyue.cloud.modules.ops.ErrorReportService.class))).build();
        String ticket = jwt.createMediaStreamTicket(1, 2, 3, 4, "VIDEO_HLS", 1800);
        mvc.perform(get("/api/v1/media/hls").param("ticket", ticket).param("file", "index.m3u8"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&file=segment-000000.ts")));
        mvc.perform(get("/api/v1/media/hls").param("ticket", ticket).param("file", "segment-000000.ts"))
                .andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1,2,3}));
        verify(drive, times(2)).mediaSourceByVerifiedTicket(2, 3, 4);
        String wrong = jwt.createMediaStreamTicket(1, 2, 3, 4, "ORIGINAL", 1800);
        mvc.perform(get("/api/v1/media/hls").param("ticket", wrong).param("file", "index.m3u8"))
                .andExpect(status().isUnauthorized());
    }
}
