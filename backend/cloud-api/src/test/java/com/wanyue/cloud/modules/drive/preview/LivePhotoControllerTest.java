package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.common.security.UserIdPrincipal;
import com.wanyue.cloud.modules.drive.DriveModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LivePhotoControllerTest {
    @TempDir Path directory;

    @Test void originalVideoEndpointStreamsOnlyTheAuthorizedSegmentWithOriginalMimeType() throws Exception {
        var file = directory.resolve("motion.jpg");
        Files.writeString(file, "JPEG-MOV-TRAILER");
        var content = new DriveModels.Content(file, Files.size(file), "motion.jpg", "image/jpeg");
        var service = mock(LivePhotoService.class);
        when(service.part(1, 2, 3, 4L, "video")).thenReturn(new LivePhotoService.Part(content, 5, 3, "original.mov", "video/quicktime"));
        var auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn((UserIdPrincipal) () -> 1L);
        var mvc = MockMvcBuilders.standaloneSetup(new LivePhotoController(service)).build();
        var pending = mvc.perform(get("/api/v1/spaces/2/nodes/3/live-photo/parts/video").param("versionId", "4").principal(auth))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(pending)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "video/quicktime"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().longValue("Content-Length", 3))
                .andExpect(content().string("MOV"));
        verify(service).part(1, 2, 3, 4L, "video");
    }
}
