package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import com.wanyue.cloud.modules.drive.DriveModels;
import com.wanyue.cloud.modules.drive.DriveService;
import io.jsonwebtoken.JwtException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * 面向浏览器原生 <video>/<audio> 的短期票据流式媒体端点。
 * 显式解析 HTTP Range 请求头并返回 206 Partial Content（ResourceRegion），支持大视频秒开与任意拖动进度条。
 */
@RestController
@RequestMapping("/api/v1/media")
public class MediaStreamController {
    /** 单次 Range 分片上限（4 MB），防止开放区间 bytes=0- 占满连接，保证 seek 响应迅速。 */
    private static final long MAX_CHUNK_SIZE = 4L * 1024 * 1024;

    private final JwtTokenProvider jwtTokenProvider;
    private final DriveService drive;
    private final PreviewAssetService assetService;

    public MediaStreamController(
            JwtTokenProvider jwtTokenProvider,
            DriveService drive,
            PreviewAssetService assetService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.drive = drive;
        this.assetService = assetService;
    }

    @GetMapping("/stream")
    public ResponseEntity<?> stream(
            @RequestParam("ticket") String ticket,
            @RequestHeader HttpHeaders requestHeaders) throws IOException {
        JwtTokenProvider.MediaStreamTicket claims;
        try {
            claims = jwtTokenProvider.verifyMediaStreamTicket(ticket);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(HttpStatus.UNAUTHORIZED, 401, "媒体播放链接已过期或无效，请重新打开");
        }

        Path path;
        String fileName;
        String mimeType;
        if ("VIDEO_MP4".equalsIgnoreCase(claims.assetType())) {
            path = assetService.resolveReadyAssetPath(claims.versionId(), "VIDEO_MP4");
            fileName = path.getFileName().toString();
            mimeType = "video/mp4";
        } else {
            DriveModels.Content content = drive.contentByVerifiedTicket(claims.spaceId(), claims.nodeId(), claims.versionId());
            path = content.path();
            fileName = content.name();
            mimeType = content.mimeType();
        }

        if (!Files.isRegularFile(path)) {
            throw BizException.notFound("媒体文件不存在");
        }
        long contentLength = Files.size(path);
        FileSystemResource resource = new FileSystemResource(path);

        MediaType mediaType;
        try {
            mediaType = (mimeType == null || mimeType.isBlank())
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(mimeType);
        } catch (InvalidMediaTypeException e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String disposition = ContentDisposition.inline().filename(fileName, StandardCharsets.UTF_8).build().toString();
        CacheControl cacheControl = CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate();

        List<HttpRange> ranges = requestHeaders.getRange();
        if (ranges.isEmpty()) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .header("X-Content-Type-Options", "nosniff")
                    .cacheControl(cacheControl)
                    .contentType(mediaType)
                    .contentLength(contentLength)
                    .body(resource);
        }

        HttpRange range = ranges.get(0);
        long start;
        long end;
        try {
            start = range.getRangeStart(contentLength);
            end = range.getRangeEnd(contentLength);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength)
                    .build();
        }
        if (contentLength == 0 || start < 0 || start >= contentLength || end < start) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength)
                    .build();
        }

        long rangeLength = Math.min(MAX_CHUNK_SIZE, end - start + 1);
        ResourceRegion region = new ResourceRegion(resource, start, rangeLength);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(cacheControl)
                .contentType(mediaType)
                .body(region);
    }
}
