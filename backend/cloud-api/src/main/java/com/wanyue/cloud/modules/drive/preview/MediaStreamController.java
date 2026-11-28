package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import com.wanyue.cloud.modules.drive.DriveModels;
import com.wanyue.cloud.modules.drive.DriveService;
import com.wanyue.cloud.modules.drive.storage.StorageRouterService;
import jakarta.servlet.http.HttpServletRequest;
import io.jsonwebtoken.JwtException;
import org.springframework.http.CacheControl;
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
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * 面向浏览器原生 <video>/<audio> 的短期票据流式媒体端点。
 * 通过 SeekableByteChannel + StreamingResponseBody 精确响应 HTTP Range（206 Partial Content），
 * 零内存堆积且不依赖 ResourceRegionHttpMessageConverter，支持任意大小视频秒开与拖动进度条。
 */
@RestController
@RequestMapping("/api/v1/media")
public class MediaStreamController {
    /** 单次 Range 分片上限（4 MB），防止开放区间 bytes=0- 占满连接，保证 seek 响应迅速。 */
    private static final long MAX_CHUNK_SIZE = 4L * 1024 * 1024;
    private static final int IO_BUFFER_SIZE = 64 * 1024;

    private final JwtTokenProvider jwtTokenProvider;
    private final DriveService drive;
    private final PreviewAssetService assetService;
    private final StorageRouterService storage;

    public MediaStreamController(
            JwtTokenProvider jwtTokenProvider,
            DriveService drive,
            PreviewAssetService assetService, StorageRouterService storage) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.drive = drive;
        this.assetService = assetService;
        this.storage = storage;
    }

    @RequestMapping(value = "/stream", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<StreamingResponseBody> stream(
            @RequestParam("ticket") String ticket,
            @RequestHeader HttpHeaders requestHeaders, HttpServletRequest request) throws IOException {
        JwtTokenProvider.MediaStreamTicket claims;
        try {
            claims = jwtTokenProvider.verifyMediaStreamTicket(ticket);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(HttpStatus.UNAUTHORIZED, 401, "媒体播放链接已过期或无效，请重新打开");
        }

        Path path = null;
        DriveModels.MediaSource source = null;
        String fileName;
        String mimeType;
        if ("VIDEO_MP4".equalsIgnoreCase(claims.assetType())) {
            path = assetService.resolveReadyAssetPath(claims.versionId(), "VIDEO_MP4");
            fileName = path.getFileName().toString();
            mimeType = "video/mp4";
        } else {
            source = drive.mediaSourceByVerifiedTicket(claims.spaceId(), claims.nodeId(), claims.versionId());
            fileName = source.name();
            mimeType = source.mimeType();
        }

        if (path != null && !Files.isRegularFile(path)) {
            throw BizException.notFound("媒体文件不存在");
        }
        long contentLength = path == null ? source.size() : Files.size(path);
        final Path localPath = path;
        final DriveModels.MediaSource remoteSource = source;
        boolean head = "HEAD".equals(request.getMethod());
        MediaType mediaType = resolveMediaType(mimeType, fileName);
        CacheControl cacheControl = CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate();

        List<HttpRange> ranges;
        try { ranges = requestHeaders.getRange(); }
        catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength).build();
        }
        if (ranges.isEmpty()) {
            StreamingResponseBody body = head ? null : body(localPath, remoteSource, 0, contentLength);
            return ResponseEntity.ok()
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header("X-Content-Type-Options", "nosniff")
                    .cacheControl(cacheControl)
                    .contentType(mediaType)
                    .contentLength(contentLength)
                    .body(body);
        }

        HttpRange range = ranges.get(0);
        long start;
        long requestedEnd;
        try {
            start = range.getRangeStart(contentLength);
            requestedEnd = range.getRangeEnd(contentLength);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength)
                    .build();
        }
        if (contentLength == 0 || start < 0 || start >= contentLength || requestedEnd < start) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + contentLength)
                    .build();
        }

        long rangeLength = Math.min(MAX_CHUNK_SIZE, requestedEnd - start + 1);
        long end = start + rangeLength - 1;
        StreamingResponseBody body = head ? null : body(localPath, remoteSource, start, rangeLength);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + contentLength)
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(cacheControl)
                .contentType(mediaType)
                .contentLength(rangeLength)
                .body(body);
    }

    private StreamingResponseBody body(Path path, DriveModels.MediaSource source, long start, long length) throws IOException {
        if (path != null) return out -> streamRange(path, start, length, out);
        if (length == 0) return out -> {};
        return out -> {
            // Open R2 inside the async response callback. Opening it here would block the
            // servlet request before headers are sent and makes the browser cancel/retry
            // when the upstream takes a moment to establish the Range response.
            try (var input = storage.openRange(source, start, start + length - 1)) {
                byte[] buffer = new byte[IO_BUFFER_SIZE];
                long remaining = length;
                while (remaining > 0) {
                    int n = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (n < 0) throw new java.io.EOFException("远程视频数据提前结束");
                    out.write(buffer, 0, n);
                    remaining -= n;
                }
                out.flush();
            }
        };
    }

    private static void streamRange(Path path, long start, long length, java.io.OutputStream out) throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
            channel.position(start);
            byte[] array = new byte[(int) Math.min(IO_BUFFER_SIZE, Math.max(1L, length))];
            long remaining = length;
            while (remaining > 0) {
                int toRead = (int) Math.min(array.length, remaining);
                ByteBuffer buf = ByteBuffer.wrap(array, 0, toRead);
                int read = channel.read(buf);
                if (read < 0) break;
                out.write(array, 0, read);
                remaining -= read;
            }
            out.flush();
        }
    }

    static MediaType resolveMediaType(String mimeType, String fileName) {
        if (mimeType != null && !mimeType.isBlank()
                && !"application/octet-stream".equalsIgnoreCase(mimeType.trim())) {
            try {
                return MediaType.parseMediaType(mimeType.trim());
            } catch (InvalidMediaTypeException ignored) {
                // fall through to extension detection
            }
        }
        String lower = fileName == null ? "" : fileName.trim().toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        String ext = (dot > 0 && dot < lower.length() - 1) ? lower.substring(dot + 1) : "";
        return switch (ext) {
            case "mp4", "m4v" -> MediaType.parseMediaType("video/mp4");
            case "webm" -> MediaType.parseMediaType("video/webm");
            case "mov" -> MediaType.parseMediaType("video/quicktime");
            case "ogv" -> MediaType.parseMediaType("video/ogg");
            case "mkv" -> MediaType.parseMediaType("video/x-matroska");
            case "avi" -> MediaType.parseMediaType("video/x-msvideo");
            case "mp3" -> MediaType.parseMediaType("audio/mpeg");
            case "wav" -> MediaType.parseMediaType("audio/wav");
            case "flac" -> MediaType.parseMediaType("audio/flac");
            case "aac" -> MediaType.parseMediaType("audio/aac");
            case "m4a" -> MediaType.parseMediaType("audio/mp4");
            case "ogg", "opus" -> MediaType.parseMediaType("audio/ogg");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
