package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.common.security.UserIdPrincipal;
import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import com.wanyue.cloud.modules.drive.DriveModels;
import com.wanyue.cloud.modules.drive.DriveService;
import com.wanyue.cloud.modules.drive.storage.StorageRouterService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MediaPlaybackControllerTest {
    final DriveService drive = mock(DriveService.class);
    final StorageRouterService storage = mock(StorageRouterService.class);
    final PreviewAssetService assets = mock(PreviewAssetService.class);
    final VideoProbeService probes = mock(VideoProbeService.class);
    final DriveModels.MediaSource source = new DriveModels.MediaSource(4, 9, "objects/video", 2_000_000_000L, "large.mp4", "video/mp4", "hash");
    final JwtTokenProvider jwt = new JwtTokenProvider(Base64.getEncoder().encodeToString("test-only-secret-at-least-32-bytes".getBytes()), "cloudlane", 900, new tools.jackson.databind.json.JsonMapper());

    PreviewController controller() {
        return new PreviewController(drive, assets, mock(ExcelPreviewService.class), mock(ArchivePreviewService.class), mock(ThumbnailService.class), jwt, storage, probes);
    }

    Authentication auth() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn((UserIdPrincipal) () -> 1L);
        when(drive.mediaSource(1, 2, 3, null)).thenReturn(source);
        when(storage.isRemote(9)).thenReturn(true);
        when(storage.playbackPolicy(9)).thenReturn(new StorageRouterService.PlaybackPolicy("AUTO", "https://media.example.test"));
        return auth;
    }

    @Test void directTicketAndProbeDoNotDownloadThreeGigabyteObject() {
        var auth = auth();
        when(storage.signedReadUrl(9, source.storageKey(), 1800, true)).thenReturn("https://storage.invalid/signed-video");
        var result = controller().createStreamTicket(auth, 2, 3, null, "ORIGINAL", false).getData();
        assertEquals("DIRECT", result.mode());
        assertNull(result.ticket());
        assertTrue(result.proxyFallbackAllowed());
        assertEquals("https://storage.invalid/signed-video", result.streamUrl());
        when(probes.getOrRequest(source)).thenReturn(new PreviewModels.VideoProbe("", "", "", 0, 0, 0, 0, "UNKNOWN", "queued", "PENDING"));
        assertEquals("PENDING", controller().videoProbe(auth, 2, 3, null).getData().status());
        verify(drive, never()).content(anyLong(), anyLong(), anyLong(), any());
        verify(drive, never()).materializeMediaSource(any());
        verify(storage, never()).resolveReadablePath(anyLong(), anyString());
    }

    @Test void explicitProxyAndForcedFallbackUseAppTicketWithoutDownloading() {
        var auth = auth();
        var forced = controller().createStreamTicket(auth, 2, 3, null, "ORIGINAL", true).getData();
        assertEquals("PROXY", forced.mode());
        assertEquals(4, jwt.verifyMediaStreamTicket(forced.ticket()).versionId());
        when(storage.playbackPolicy(9)).thenReturn(new StorageRouterService.PlaybackPolicy("PROXY", ""));
        assertEquals("PROXY", controller().createStreamTicket(auth, 2, 3, null, "ORIGINAL", false).getData().mode());
        verify(storage, never()).signedReadUrl(anyLong(), anyString(), anyInt(), anyBoolean());
        verify(drive, never()).materializeMediaSource(any());
    }

    @Test void autoPolicyWithoutBrowserEndpointUsesProxyTicket() {
        var auth = auth();
        when(storage.playbackPolicy(9)).thenReturn(new StorageRouterService.PlaybackPolicy("AUTO", ""));
        var result = controller().createStreamTicket(auth, 2, 3, null, "ORIGINAL", false).getData();
        assertEquals("PROXY", result.mode());
        assertNotNull(result.ticket());
        verify(storage, never()).signedReadUrl(anyLong(), anyString(), anyInt(), anyBoolean());
    }

    @Test void directOnlyPolicyDoesNotAdvertiseAutomaticFallback() {
        var auth = auth();
        when(storage.playbackPolicy(9)).thenReturn(new StorageRouterService.PlaybackPolicy("DIRECT", ""));
        when(storage.signedReadUrl(9, source.storageKey(), 1800, true)).thenReturn("https://storage.invalid/signed-video");
        assertFalse(controller().createStreamTicket(auth, 2, 3, null, "ORIGINAL", false).getData().proxyFallbackAllowed());
    }

    @Test void proxyForwardsOnlyRequestedBytesAndHeadDoesNotReadObject() throws Exception {
        auth();
        when(drive.mediaSourceByVerifiedTicket(2, 3, 4)).thenReturn(source);
        when(storage.openRange(source, 10, 19)).thenReturn(new ByteArrayInputStream("abcdefghij".getBytes()));
        var mvc = MockMvcBuilders.standaloneSetup(new MediaStreamController(jwt, drive, assets, storage)).build();
        String ticket = jwt.createMediaStreamTicket(1, 2, 3, 4, "ORIGINAL", 1800);
        var pending = mvc.perform(get("/api/v1/media/stream").param("ticket", ticket).header(HttpHeaders.RANGE, "bytes=10-19"))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(pending)).andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 10-19/" + source.size()))
                .andExpect(content().string("abcdefghij"));
        mvc.perform(head("/api/v1/media/stream").param("ticket", ticket)).andExpect(status().isOk())
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, source.size())).andExpect(content().string(""));
        mvc.perform(get("/api/v1/media/stream").param("ticket", ticket).header(HttpHeaders.RANGE, "bytes=9999999999-"))
                .andExpect(status().isRequestedRangeNotSatisfiable());
        verify(storage, times(1)).openRange(source, 10, 19);
        verify(drive, never()).materializeMediaSource(any());
        verify(drive, never()).contentByVerifiedTicket(anyLong(), anyLong(), anyLong());
    }

    @Test void authorizationFailureNeverIssuesStorageAddress() {
        var auth = auth();
        when(drive.mediaSource(1, 2, 3, null)).thenThrow(com.wanyue.cloud.common.exception.BizException.notFound("denied"));
        assertThrows(com.wanyue.cloud.common.exception.BizException.class, () -> controller().createStreamTicket(auth, 2, 3, null, "ORIGINAL", false));
        verify(storage, never()).signedReadUrl(anyLong(), anyString(), anyInt(), anyBoolean());
    }
}
