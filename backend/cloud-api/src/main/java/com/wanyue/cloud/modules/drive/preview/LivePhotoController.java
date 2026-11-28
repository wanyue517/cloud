package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.common.security.UserIdPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/spaces/{space}/nodes/{id}/live-photo")
public class LivePhotoController {
    private final LivePhotoService service;
    public LivePhotoController(LivePhotoService service) { this.service = service; }
    public record PairInput(@Positive long versionId, @Positive long videoNodeId, @Positive Long posterNodeId) {}

    @GetMapping
    public R<LivePhotoService.State> inspect(Authentication auth, @PathVariable long space, @PathVariable long id,
            @RequestParam(required = false) Long versionId, @RequestParam(defaultValue = "false") boolean retry) throws IOException {
        return R.ok(service.inspect(((UserIdPrincipal) auth.getPrincipal()).userId(), space, id, versionId, retry));
    }

    @PostMapping
    public R<LivePhotoService.State> pair(Authentication auth, @PathVariable long space, @PathVariable long id,
            @Valid @RequestBody PairInput input) throws IOException {
        return R.ok(service.pair(((UserIdPrincipal) auth.getPrincipal()).userId(), space, id, input.versionId(), input.videoNodeId(), input.posterNodeId()));
    }

    @GetMapping("/parts/{kind}")
    public ResponseEntity<StreamingResponseBody> originalPart(Authentication auth, @PathVariable long space, @PathVariable long id,
            @PathVariable String kind, @RequestParam(required = false) Long versionId) throws IOException {
        var part = service.part(((UserIdPrincipal) auth.getPrincipal()).userId(), space, id, versionId, kind);
        StreamingResponseBody body = output -> {
            try (var input = new java.io.RandomAccessFile(part.content().path().toFile(), "r")) {
                input.seek(part.offset());
                byte[] buffer = new byte[64 * 1024];
                long remaining = part.length();
                while (remaining > 0) {
                    int n = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (n < 0) throw new java.io.EOFException("实况资源不完整");
                    output.write(buffer, 0, n);
                    remaining -= n;
                }
            }
        };
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentLength(part.length())
                .contentType(MediaType.parseMediaType(part.mimeType()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(part.name(), StandardCharsets.UTF_8).build().toString())
                .body(body);
    }

    @GetMapping("/motion-photo")
    public ResponseEntity<StreamingResponseBody> motionPhoto(Authentication auth, @PathVariable long space, @PathVariable long id,
            @RequestParam(required = false) Long versionId) throws IOException {
        var export = service.motionExport(((UserIdPrincipal) auth.getPrincipal()).userId(), space, id, versionId);
        StreamingResponseBody body = output -> MotionPhotoWriter.write(export.photo(), export.video(), output);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_JPEG)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("Cloudlane-" + id + "MP.jpg").build().toString())
                .body(body);
    }
}
