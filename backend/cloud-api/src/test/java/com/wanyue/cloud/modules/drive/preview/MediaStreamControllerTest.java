package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import com.wanyue.cloud.modules.drive.DriveModels;
import com.wanyue.cloud.modules.drive.DriveService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MediaStreamControllerTest {
    @TempDir
    Path tempDir;

    @Test
    void rangeRequestReturns206WithVideoMp4ContentTypeAndExactBytes() throws Exception {
        byte[] data = "0123456789abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8);
        Path video = tempDir.resolve("demo.mp4");
        Files.write(video, data);

        byte[] secret = "test-only-secret-at-least-32-bytes".getBytes(StandardCharsets.UTF_8);
        JwtTokenProvider jwt = new JwtTokenProvider(Base64.getEncoder().encodeToString(secret), "cloudlane", 900, new JsonMapper());
        DriveService drive = mock(DriveService.class);
        PreviewAssetService assetService = mock(PreviewAssetService.class);
        // 故意模拟上传时 MIME 存成了 application/octet-stream
        when(drive.contentByVerifiedTicket(2L, 3L, 4L))
                .thenReturn(new DriveModels.Content(video, data.length, "demo.mp4", "application/octet-stream", "abc"));

        String ticket = jwt.createMediaStreamTicket(1L, 2L, 3L, 4L, "ORIGINAL", 1800);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MediaStreamController(jwt, drive, assetService)).build();

        var mvcResult = mvc.perform(get("/api/v1/media/stream")
                        .param("ticket", ticket)
                        .header(HttpHeaders.RANGE, "bytes=10-19"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(mvcResult))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 10-19/" + data.length))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "video/mp4"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 10L))
                .andExpect(content().bytes("abcdefghij".getBytes(StandardCharsets.UTF_8)));
    }
}
