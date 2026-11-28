package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.common.security.UserIdPrincipal;
import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import com.wanyue.cloud.modules.drive.DriveModels;
import com.wanyue.cloud.modules.drive.DriveService;
import com.wanyue.cloud.modules.drive.storage.StorageRouterService;
import com.wanyue.cloud.modules.drive.preview.PreviewModels.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/spaces/{space}/nodes/{id}/preview")
public class PreviewController {
    private final DriveService drive;
    private final PreviewAssetService assetService;
    private final ExcelPreviewService excelService;
    private final ArchivePreviewService archiveService;
    private final ThumbnailService thumbnailService;
    private final JwtTokenProvider jwtTokenProvider;
    private final StorageRouterService storage;
    private final VideoProbeService probes;

    public PreviewController(
            DriveService drive,
            PreviewAssetService assetService,
            ExcelPreviewService excelService,
            ArchivePreviewService archiveService,
            ThumbnailService thumbnailService,
            JwtTokenProvider jwtTokenProvider, StorageRouterService storage, VideoProbeService probes) {
        this.drive = drive;
        this.assetService = assetService;
        this.excelService = excelService;
        this.archiveService = archiveService;
        this.thumbnailService = thumbnailService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.storage = storage;
        this.probes = probes;
    }

    private static long user(Authentication auth) {
        return ((UserIdPrincipal) auth.getPrincipal()).userId();
    }

    private long resolveVersionId(long userId, long space, long id, Long versionId) {
        if (versionId != null) return versionId;
        var versions = drive.versions(userId, space, id);
        return versions.isEmpty() ? id : Long.parseLong(versions.get(0).id());
    }

    @GetMapping("/assets/{assetType}")
    public R<PreviewAsset> getOrTriggerAsset(
            Authentication auth,
            @PathVariable long space,
            @PathVariable long id,
            @PathVariable String assetType,
            @RequestParam(required = false) Long versionId) {
        long uid = user(auth);
        if ("VIDEO_MP4".equalsIgnoreCase(assetType)) {
            var source = drive.mediaSource(uid, space, id, versionId);
            return R.ok(assetService.getOrTriggerAsset(id, source.versionId(), assetType, source.size(), () -> drive.materializeMediaSource(source)));
        }
        DriveModels.Content content = drive.content(uid, space, id, versionId);
        long resolvedVersion = resolveVersionId(uid, space, id, versionId);
        return R.ok(assetService.getOrTriggerAsset(id, resolvedVersion, assetType, content));
    }

    @PostMapping("/assets/{assetType}/retry")
    public R<PreviewAsset> retryAsset(
            Authentication auth,
            @PathVariable long space,
            @PathVariable long id,
            @PathVariable String assetType,
            @RequestParam(required = false) Long versionId) {
        long uid = user(auth);
        if ("VIDEO_MP4".equalsIgnoreCase(assetType)) {
            var source = drive.mediaSource(uid, space, id, versionId);
            return R.ok(assetService.retryAsset(id, source.versionId(), assetType, source.size(), () -> drive.materializeMediaSource(source)));
        }
        DriveModels.Content content = drive.content(uid, space, id, versionId);
        long resolvedVersion = resolveVersionId(uid, space, id, versionId);
        return R.ok(assetService.retryAsset(id, resolvedVersion, assetType, content));
    }

    @GetMapping("/assets/{assetType}/content")
    public ResponseEntity<FileSystemResource> assetContent(
            Authentication auth,
            @PathVariable long space,
            @PathVariable long id,
            @PathVariable String assetType,
            @RequestParam(required = false) Long versionId,
            WebRequest webRequest) {
        long uid = user(auth);
        drive.mediaSource(uid, space, id, versionId);
        long resolvedVersion = resolveVersionId(uid, space, id, versionId);
        Path assetPath = assetService.resolveReadyAssetPath(resolvedVersion, assetType);
        String etag = "\"asset-" + resolvedVersion + "-" + assetType.toUpperCase() + "\"";
        CacheControl cc = CacheControl.maxAge(Duration.ofHours(1)).cachePrivate();
        if (webRequest.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cc).build();
        }
        String mime = "OFFICE_PDF".equalsIgnoreCase(assetType) ? "application/pdf" : "video/mp4";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(assetPath.getFileName().toString(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .eTag(etag)
                .cacheControl(cc)
                .contentType(MediaType.parseMediaType(mime))
                .body(new FileSystemResource(assetPath));
    }

    @GetMapping("/excel/sheets")
    public R<ExcelMetadata> excelSheets(
            Authentication auth,
            @PathVariable long space,
            @PathVariable long id,
            @RequestParam(required = false) Long versionId) throws IOException {
        DriveModels.Content content = drive.content(user(auth), space, id, versionId);
        return R.ok(excelService.readMetadata(content.path(), content.name()));
    }

    @GetMapping("/excel/rows")
    public R<ExcelRowsPage> excelRows(
            Authentication auth,
            @PathVariable long space,
            @PathVariable long id,
            @RequestParam(required = false) Long versionId,
            @RequestParam(defaultValue = "0") int sheetIndex,
            @RequestParam(defaultValue = "0") int rowOffset,
            @RequestParam(defaultValue = "100") int rowLimit) throws IOException {
        DriveModels.Content content = drive.content(user(auth), space, id, versionId);
        return R.ok(excelService.readRows(content.path(), content.name(), sheetIndex, rowOffset, rowLimit));
    }

    @GetMapping("/video/probe")
    public R<VideoProbe> videoProbe(
            Authentication auth,
            @PathVariable long space,
            @PathVariable long id,
            @RequestParam(required = false) Long versionId) {
        return R.ok(probes.getOrRequest(drive.mediaSource(user(auth), space, id, versionId)));
    }

    public record StreamTicketResponse(String ticket, String streamUrl, long expiresInSeconds, String mode, long expiresAt, boolean proxyFallbackAllowed) {}

    @PostMapping("/stream-ticket")
    public R<StreamTicketResponse> createStreamTicket(
            Authentication auth,
            @PathVariable long space,
            @PathVariable long id,
            @RequestParam(required = false) Long versionId,
            @RequestParam(defaultValue = "ORIGINAL") String assetType,
            @RequestParam(defaultValue = "false") boolean forceProxy) {
        long uid = user(auth);
        var source = drive.mediaSource(uid, space, id, versionId);
        long resolvedVersion = source.versionId();
        String normalizedAsset = "VIDEO_MP4".equalsIgnoreCase(assetType) ? "VIDEO_MP4" : "ORIGINAL";
        if ("VIDEO_MP4".equals(normalizedAsset)) {
            assetService.resolveReadyAssetPath(resolvedVersion, "VIDEO_MP4");
        }
        long ttlSeconds = 1800L;
        long expiresAt = java.time.Instant.now().plusSeconds(ttlSeconds).toEpochMilli();
        boolean video = (source.mimeType() != null && source.mimeType().startsWith("video/"))
                || source.name().toLowerCase(java.util.Locale.ROOT).matches(".*\\.(mp4|m4v|mov|mkv|avi|webm|ogv)$");
        var playbackPolicy = storage.isRemote(source.profileId())
                ? storage.playbackPolicy(source.profileId())
                : new StorageRouterService.PlaybackPolicy("PROXY", "");
        String policy = playbackPolicy.mode();
        boolean directAllowed = "DIRECT".equals(policy)
                || ("AUTO".equals(policy) && !playbackPolicy.endpoint().isBlank());
        if (video && "ORIGINAL".equals(normalizedAsset) && !forceProxy && directAllowed) {
            try {
                String url = storage.signedReadUrl(source.profileId(), source.storageKey(), (int) ttlSeconds, true);
                return R.ok(new StreamTicketResponse(null, url, ttlSeconds, "DIRECT", expiresAt, "AUTO".equals(policy)));
            } catch (RuntimeException e) {
                if (!"AUTO".equals(policy)) throw e;
            }
        }
        String ticket = jwtTokenProvider.createMediaStreamTicket(uid, space, id, resolvedVersion, normalizedAsset, ttlSeconds);
        String streamUrl = "/api/v1/media/stream?ticket=" + java.net.URLEncoder.encode(ticket, StandardCharsets.UTF_8);
        return R.ok(new StreamTicketResponse(ticket, streamUrl, ttlSeconds, "PROXY", expiresAt, false));
    }

    @GetMapping("/archive/entries")
    public R<ArchiveListing> archiveEntries(
            Authentication auth,
            @PathVariable long space,
            @PathVariable long id,
            @RequestParam(required = false) Long versionId) throws IOException {
        DriveModels.Content content = drive.content(user(auth), space, id, versionId);
        return R.ok(archiveService.listEntries(content.path(), content.name()));
    }

    @GetMapping("/archive/extract")
    public ResponseEntity<ByteArrayResource> archiveExtract(
            Authentication auth,
            @PathVariable long space,
            @PathVariable long id,
            @RequestParam(required = false) Long versionId,
            @RequestParam String entryPath) throws IOException {
        DriveModels.Content content = drive.content(user(auth), space, id, versionId);
        ArchivePreviewService.ExtractedEntry extracted = archiveService.extractEntry(content.path(), content.name(), entryPath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(extracted.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new ByteArrayResource(extracted.data()));
    }

    @GetMapping("/thumbnail")
    public ResponseEntity<FileSystemResource> thumbnail(
            Authentication auth,
            @PathVariable long space,
            @PathVariable long id,
            @RequestParam(required = false) Long versionId,
            @RequestParam(defaultValue = "320") int width,
            WebRequest webRequest) {
        long uid = user(auth);
        var source = drive.mediaSource(uid, space, id, versionId);
        long resolvedVersion = resolveVersionId(uid, space, id, versionId);
        String etag = "\"thumb-" + resolvedVersion + "-" + width + "\"";
        CacheControl cc = CacheControl.maxAge(Duration.ofHours(24)).cachePrivate();
        if (webRequest.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cc).build();
        }
        java.util.Optional<java.nio.file.Path> thumb;
        if (storage.isRemote(source.profileId()) && isVideo(source)) {
            String input = storage.signedReadUrl(source.profileId(), source.storageKey(), 300, false);
            thumb = thumbnailService.getOrGenerateVideo(resolvedVersion, source.name(), input, width);
        } else {
            thumb = thumbnailService.getOrGenerate(resolvedVersion, drive.materializeMediaSource(source), width);
        }
        if (thumb.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header("X-Content-Type-Options", "nosniff")
                .eTag(etag)
                .cacheControl(cc)
                .contentType(MediaType.IMAGE_JPEG)
                .body(new FileSystemResource(thumb.get()));
    }

    private static boolean isVideo(DriveModels.MediaSource source) {
        return (source.mimeType() != null && source.mimeType().startsWith("video/"))
                || source.name().toLowerCase(java.util.Locale.ROOT).matches(".*\\.(mp4|m4v|mov|mkv|avi|webm|ogv|flv|wmv)$");
    }
}
