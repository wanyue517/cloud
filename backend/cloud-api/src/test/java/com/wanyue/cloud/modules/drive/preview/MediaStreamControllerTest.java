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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        var source = new DriveModels.MediaSource(4L, 1L, "key", data.length, "demo.mp4", "application/octet-stream", "abc");
        when(drive.mediaSourceByVerifiedTicket(2L, 3L, 4L)).thenReturn(source);
        String ticket = jwt.createMediaStreamTicket(1L, 2L, 3L, 4L, "ORIGINAL", 1800);
        var storage = mock(com.wanyue.cloud.modules.drive.storage.StorageRouterService.class);
        when(storage.openRange(source, 10, 19)).thenReturn(new java.io.ByteArrayInputStream("abcdefghij".getBytes(StandardCharsets.UTF_8)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MediaStreamController(jwt, drive, assetService, storage)).build();

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
        verify(drive, never()).materializeMediaSource(source);
    }

    @Test
    void openEndedLargeRangeAndTailAreNotTruncated() throws Exception {
        byte[] secret = "test-only-secret-at-least-32-bytes".getBytes(StandardCharsets.UTF_8);
        JwtTokenProvider jwt = new JwtTokenProvider(Base64.getEncoder().encodeToString(secret), "cloudlane", 900, new JsonMapper());
        DriveService drive = mock(DriveService.class);
        PreviewAssetService assets = mock(PreviewAssetService.class);
        var storage = mock(com.wanyue.cloud.modules.drive.storage.StorageRouterService.class);
        long size = 3L * 1024 * 1024 * 1024;
        var source = new DriveModels.MediaSource(4L, 9L, "large", size, "large.mp4", "video/mp4", "abc");
        when(drive.mediaSourceByVerifiedTicket(2L, 3L, 4L)).thenReturn(source);
        String ticket = jwt.createMediaStreamTicket(1L, 2L, 3L, 4L, "ORIGINAL", 1800);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new MediaStreamController(jwt, drive, assets, storage)).build();

        // HEAD can assert a multi-gigabyte range without allocating the payload.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head("/api/v1/media/stream")
                        .param("ticket", ticket).header(HttpHeaders.RANGE, "bytes=0-"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-" + (size - 1) + "/" + size))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, size));

        long start = size - 8;
        byte[] tail = "moovtail".getBytes(StandardCharsets.UTF_8);
        when(storage.openRange(source, start, size - 1)).thenReturn(new java.io.ByteArrayInputStream(tail));
        var pending = mvc.perform(get("/api/v1/media/stream").param("ticket", ticket)
                        .header(HttpHeaders.RANGE, "bytes=-8"))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(pending))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + (size - 1) + "/" + size))
                .andExpect(content().bytes(tail));

        // An actual range above the previous 4 MiB limit must also be streamed in full.
        int length = 5 * 1024 * 1024;
        byte[] data = new byte[length];
        data[length - 1] = 42;
        when(storage.openRange(source, 0, length - 1L)).thenReturn(new java.io.ByteArrayInputStream(data));
        var large = mvc.perform(get("/api/v1/media/stream").param("ticket", ticket)
                        .header(HttpHeaders.RANGE, "bytes=0-" + (length - 1L)))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(large))
                .andExpect(status().isPartialContent())
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, length))
                .andExpect(content().bytes(data));
        verify(drive, never()).materializeMediaSource(source);
    }
}
