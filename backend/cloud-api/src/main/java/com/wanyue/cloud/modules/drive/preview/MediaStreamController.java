package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import com.wanyue.cloud.modules.drive.DriveModels;
import com.wanyue.cloud.modules.drive.DriveService;
import io.jsonwebtoken.JwtException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

/**
 * 面向浏览器原生 <video>/<audio> 的短期票据流式媒体端点。
 * 通过 Spring FileSystemResource 原生支持 HTTP Range（206 Partial Content），避免前端整包拉取 Blob。
 */
@RestController
@RequestMapping("/api/v1/media")
public class MediaStreamController {
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
    public ResponseEntity<FileSystemResource> stream(@RequestParam("ticket") String ticket) {
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

        MediaType mediaType;
        try {
            mediaType = (mimeType == null || mimeType.isBlank())
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(mimeType);
        } catch (InvalidMediaTypeException e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .contentType(mediaType)
                .body(new FileSystemResource(path));
    }
}
