package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import com.wanyue.cloud.modules.drive.DriveService;
import io.jsonwebtoken.JwtException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/media")
public class HlsMediaController {
    private final JwtTokenProvider jwt;
    private final DriveService drive;
    private final VideoStreamingService streaming;

    public HlsMediaController(JwtTokenProvider jwt, DriveService drive, VideoStreamingService streaming) {
        this.jwt = jwt; this.drive = drive; this.streaming = streaming;
    }

    @RequestMapping(value = "/hls", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<?> hls(@RequestParam String ticket, @RequestParam String file) throws IOException {
        JwtTokenProvider.MediaStreamTicket claims;
        try {
            claims = jwt.verifyMediaStreamTicket(ticket);
            if (!"VIDEO_HLS".equals(claims.assetType())) throw new IllegalArgumentException();
        } catch (JwtException | IllegalArgumentException invalid) {
            throw new BizException(HttpStatus.UNAUTHORIZED, 401, "视频链接已过期，请重新打开");
        }
        drive.mediaSourceByVerifiedTicket(claims.spaceId(), claims.nodeId(), claims.versionId());
        if ("FAILED".equals(streaming.status(claims.versionId()).status()))
            throw new BizException(HttpStatus.GONE, 410, "视频处理已中断，请重试");
        var path = streaming.file(claims.versionId(), file);
        if ("index.m3u8".equals(file)) {
            var result = new StringBuilder();
            for (String line : Files.readAllLines(path)) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    // Only our generated segment names can be published into the playlist.
                    streaming.file(claims.versionId(), line);
                    line = "/api/v1/media/hls?ticket=" + URLEncoder.encode(ticket, StandardCharsets.UTF_8) + "&file=" + line;
                }
                result.append(line).append('\n');
            }
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                    .header("X-Content-Type-Options", "nosniff").body(result.toString());
        }
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .contentType(MediaType.parseMediaType("video/mp2t"))
                .header("X-Content-Type-Options", "nosniff").body(new FileSystemResource(path));
    }
}
