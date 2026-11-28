package com.wanyue.cloud.modules.drive.preview;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.drive.DriveModels;
import com.wanyue.cloud.modules.drive.preview.PreviewModels.*;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;

@Service
public class PreviewAssetService {
    private static final Set<String> BROWSER_DIRECT_VIDEO_CODECS = Set.of("h264", "avc1", "vp8", "vp9", "av1");
    private static final Set<String> BROWSER_DIRECT_AUDIO_CODECS = Set.of("aac", "mp3", "opus", "vorbis", "flac", "");
    private static final Set<String> BROWSER_DIRECT_CONTAINERS = Set.of("mp4", "mov,mp4,m4a,3gp,3g2,mj2", "webm", "matroska,webm");

    private final JdbcTemplate db;
    private final Path previewRoot;
    private final String gotenbergUrl;
    private final String ffmpegBin;
    private final String ffprobeBin;
    private final int timeoutSeconds;
    private final long maxSourceBytes;
    private final Semaphore concurrencyGuard;
    private final ExecutorService executor;
    private final HttpClient httpClient;

    public PreviewAssetService(
            JdbcTemplate db,
            @Value("${cloud.storage.preview-root:data/previews}") String previewRoot,
            @Value("${cloud.preview.gotenberg-url:http://127.0.0.1:3002}") String gotenbergUrl,
            @Value("${cloud.preview.ffmpeg-bin:ffmpeg}") String ffmpegBin,
            @Value("${cloud.preview.ffprobe-bin:ffprobe}") String ffprobeBin,
            @Value("${cloud.preview.timeout-seconds:60}") int timeoutSeconds,
            @Value("${cloud.preview.max-concurrency:2}") int maxConcurrency,
            @Value("${cloud.preview.max-source-bytes:209715200}") long maxSourceBytes) throws IOException {
        this.db = db;
        this.previewRoot = Path.of(previewRoot).toAbsolutePath().normalize();
        Files.createDirectories(this.previewRoot);
        this.gotenbergUrl = gotenbergUrl.replaceAll("/+$", "");
        this.ffmpegBin = ffmpegBin;
        this.ffprobeBin = ffprobeBin;
        this.timeoutSeconds = Math.max(5, timeoutSeconds);
        this.maxSourceBytes = Math.max(1024L * 1024, maxSourceBytes);
        int pool = Math.max(1, maxConcurrency);
        this.concurrencyGuard = new Semaphore(pool);
        this.executor = Executors.newFixedThreadPool(pool, r -> {
            Thread t = new Thread(r, "preview-worker");
            t.setDaemon(true);
            return t;
        });
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public PreviewAsset getOrTriggerAsset(long fileId, long versionId, String assetType, DriveModels.Content source) {
        return getOrTriggerAsset(fileId, versionId, assetType, source.size(), () -> source);
    }

    public PreviewAsset getOrTriggerAsset(long fileId, long versionId, String assetType, long sourceSize,
                                          java.util.function.Supplier<DriveModels.Content> source) {
        String normalizedType = normalizeAssetType(assetType);
        if (sourceSize > maxSourceBytes) {
            throw new BizException(HttpStatus.PAYLOAD_TOO_LARGE, 413, "文件体积超出在线转换上限，请下载后查看");
        }
        Optional<PreviewAsset> existing = findAsset(versionId, normalizedType);
        if (existing.isPresent()) {
            return existing.get();
        }
        long id = IdWorker.getId();
        db.update(
                "INSERT IGNORE INTO preview_asset(id, file_id, version_id, asset_type, status, attempt_count) VALUES (?, ?, ?, ?, 'PENDING', 1)",
                id, fileId, versionId, normalizedType);
        PreviewAsset current = findAsset(versionId, normalizedType)
                .orElseThrow(() -> BizException.notFound("预览派生记录创建失败"));
        if ("PENDING".equals(current.status())) {
            submitAsync(current.id(), versionId, normalizedType, source);
        }
        return current;
    }

    public PreviewAsset retryAsset(long fileId, long versionId, String assetType, DriveModels.Content source) {
        return retryAsset(fileId, versionId, assetType, source.size(), () -> source);
    }

    public PreviewAsset retryAsset(long fileId, long versionId, String assetType, long sourceSize,
                                   java.util.function.Supplier<DriveModels.Content> source) {
        String normalizedType = normalizeAssetType(assetType);
        Optional<PreviewAsset> existing = findAsset(versionId, normalizedType);
        if (existing.isEmpty()) {
            return getOrTriggerAsset(fileId, versionId, normalizedType, sourceSize, source);
        }
        PreviewAsset asset = existing.get();
        if (sourceSize > maxSourceBytes) throw new BizException(HttpStatus.PAYLOAD_TOO_LARGE, 413, "文件体积超出在线转换上限，请下载后查看");
        if ("READY".equals(asset.status()) || "PROCESSING".equals(asset.status())) {
            return asset;
        }
        db.update(
                "UPDATE preview_asset SET status='PENDING', error_message=NULL, attempt_count=attempt_count+1, updated_at=UTC_TIMESTAMP(3) WHERE id=?",
                Long.parseLong(asset.id()));
        submitAsync(asset.id(), versionId, normalizedType, source);
        return findAsset(versionId, normalizedType).orElse(asset);
    }

    public Path resolveReadyAssetPath(long versionId, String assetType) {
        String normalizedType = normalizeAssetType(assetType);
        var rows = db.queryForList(
                "SELECT status, storage_key FROM preview_asset WHERE version_id=? AND asset_type=?",
                versionId, normalizedType);
        if (rows.isEmpty() || !"READY".equals(rows.get(0).get("status"))) {
            throw BizException.notFound("预览派生资源尚未就绪");
        }
        String key = String.valueOf(rows.get(0).get("storage_key"));
        Path resolved = previewRoot.resolve(key).normalize();
        if (!resolved.startsWith(previewRoot) || !Files.isRegularFile(resolved)) {
            throw BizException.notFound("预览派生文件不存在");
        }
        return resolved;
    }

    public VideoProbe probeVideo(DriveModels.Content source) {
        return probeInput(source.path().toAbsolutePath().toString(), source.name());
    }

    public VideoProbe probeInput(String input, String fileName) {
        String ext = extensionOf(fileName);
        boolean remote = input.startsWith("https://") || input.startsWith("http://");
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffprobeBin,
                    "-v", "quiet",
                    "-protocol_whitelist", remote ? "http,https,tcp,tls" : "file",
                    "-format_whitelist", "mov,matroska,webm,avi,mpegts,mpeg,flv,ogg,asf,mp3,aac,wav,flac",
                    "-rw_timeout", "10000000",
                    "-probesize", "5242880",
                    "-analyzeduration", "5000000",
                    "-print_format", "json",
                    "-show_format",
                    "-show_streams",
                    input);
            process = pb.start();
            Process running = process;
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try (InputStream in = running.getInputStream()) {
                    in.transferTo(stdout);
                } catch (IOException ignored) {}
            });
            reader.start();
            boolean finished = process.waitFor(Math.min(15, timeoutSeconds), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("ffprobe 探测超时");
            }
            reader.join(2000);
            if (process.exitValue() != 0) {
                throw new IOException("ffprobe 返回非零状态码");
            }
            return parseFfprobeJson(stdout.toString(StandardCharsets.UTF_8), ext);
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return new VideoProbe(ext, "", "", 0, 0, 0, 0, "UNKNOWN",
                    "视频探测未完成，请尝试原文件播放或重新探测", "FAILED");
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    public static VideoProbe decideVideoStrategy(
            String container, String videoCodec, String audioCodec,
            double durationSeconds, int width, int height, long bitrate) {
        String c = container == null ? "" : container.toLowerCase(Locale.ROOT);
        String vc = videoCodec == null ? "" : videoCodec.toLowerCase(Locale.ROOT);
        String ac = audioCodec == null ? "" : audioCodec.toLowerCase(Locale.ROOT);

        boolean webContainer = BROWSER_DIRECT_CONTAINERS.contains(c) || c.contains("mp4") || c.contains("webm");
        boolean webVideo = BROWSER_DIRECT_VIDEO_CODECS.contains(vc);
        boolean webAudio = BROWSER_DIRECT_AUDIO_CODECS.contains(ac);

        if (webContainer && webVideo && webAudio) {
            return new VideoProbe(c, vc, ac, durationSeconds, width, height, bitrate,
                    "DIRECT_PLAY", "编码与容器原生兼容浏览器，直接流式播放");
        }
        if (webVideo && webAudio) {
            return new VideoProbe(c, vc, ac, durationSeconds, width, height, bitrate,
                    "REMUX", "视频/音频编码兼容，仅需快速转封装为 MP4");
        }
        return new VideoProbe(c, vc, ac, durationSeconds, width, height, bitrate,
                "TRANSCODE", "编码格式不兼容浏览器，需转码为 H.264/AAC MP4");
    }

    private static VideoProbe parseFfprobeJson(String json, String fallbackExt) {
        var mapper = new tools.jackson.databind.json.JsonMapper();
        var root = mapper.readTree(json);
        String container = fallbackExt;
        double duration = 0;
        long bitrate = 0;
        var format = root.get("format");
        if (format != null) {
            if (format.has("format_name")) container = format.get("format_name").asString();
            if (format.has("duration")) duration = parseDouble(format.get("duration").asString());
            if (format.has("bit_rate")) bitrate = parseLong(format.get("bit_rate").asString());
        }
        String videoCodec = "";
        String audioCodec = "";
        int width = 0;
        int height = 0;
        var streams = root.get("streams");
        if (streams != null && streams.isArray()) {
            for (var s : streams) {
                String type = s.has("codec_type") ? s.get("codec_type").asString() : "";
                if ("video".equals(type) && videoCodec.isEmpty()) {
                    videoCodec = s.has("codec_name") ? s.get("codec_name").asString() : "";
                    width = s.has("width") ? s.get("width").asInt() : 0;
                    height = s.has("height") ? s.get("height").asInt() : 0;
                } else if ("audio".equals(type) && audioCodec.isEmpty()) {
                    audioCodec = s.has("codec_name") ? s.get("codec_name").asString() : "";
                }
            }
        }
        return decideVideoStrategy(container, videoCodec, audioCodec, duration, width, height, bitrate);
    }

    private void submitAsync(String assetId, long versionId, String assetType, java.util.function.Supplier<DriveModels.Content> loader) {
        executor.submit(() -> {
            boolean acquired = false;
            try {
                acquired = concurrencyGuard.tryAcquire(timeoutSeconds, TimeUnit.SECONDS);
                if (!acquired) {
                    markFailed(assetId, "转换队列繁忙，等待资源超时，请稍后重试");
                    return;
                }
                db.update("UPDATE preview_asset SET status='PROCESSING', updated_at=UTC_TIMESTAMP(3) WHERE id=?",
                        Long.parseLong(assetId));
                DriveModels.Content source = loader.get();
                if ("OFFICE_PDF".equals(assetType)) {
                    convertOfficeViaGotenberg(assetId, versionId, source);
                } else if ("VIDEO_MP4".equals(assetType)) {
                    transcodeVideoViaFfmpeg(assetId, versionId, source);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                markFailed(assetId, "转换任务被中断");
            } catch (Exception e) {
                markFailed(assetId, e.getMessage() == null ? "预览转换失败" : e.getMessage());
            } finally {
                if (acquired) concurrencyGuard.release();
            }
        });
    }

    private void convertOfficeViaGotenberg(String assetId, long versionId, DriveModels.Content source) throws Exception {
        String boundary = "----CloudlaneGotenberg" + UUID.randomUUID().toString().replace("-", "");
        byte[] fileBytes = Files.readAllBytes(source.path());
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"files\"; filename=\"" + source.name().replace("\"", "_") + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        body.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(fileBytes);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(gotenbergUrl + "/forms/libreoffice/convert"))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<byte[]> resp;
        try {
            resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new IOException("Gotenberg 转换服务未启动或不可达 (" + gotenbergUrl + ")，请启用 office profile 后重试");
        }
        if (resp.statusCode() != 200 || resp.body() == null || resp.body().length == 0) {
            throw new IOException("Gotenberg 转换失败，状态码: " + resp.statusCode());
        }
        String key = "office/" + versionId + ".pdf";
        Path target = previewRoot.resolve(key).normalize();
        Files.createDirectories(target.getParent());
        Files.write(target, resp.body(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        markReady(assetId, key, "application/pdf", resp.body().length);
    }

    private void transcodeVideoViaFfmpeg(String assetId, long versionId, DriveModels.Content source) throws Exception {
        VideoProbe probe = probeVideo(source);
        String key = "video/" + versionId + ".mp4";
        Path target = previewRoot.resolve(key).normalize();
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(versionId + ".tmp.mp4");

        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpegBin);
        cmd.add("-y");
        cmd.addAll(List.of("-protocol_whitelist", "file",
                "-format_whitelist", "mov,matroska,webm,avi,mpegts,mpeg,flv,ogg,asf"));
        cmd.add("-i");
        cmd.add(source.path().toAbsolutePath().toString());
        if ("REMUX".equals(probe.strategy())) {
            cmd.addAll(List.of("-c", "copy", "-movflags", "+faststart"));
        } else {
            cmd.addAll(List.of("-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
                    "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart"));
        }
        cmd.add(temp.toAbsolutePath().toString());

        Process process;
        try {
            process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new IOException("FFmpeg 未安装或不可执行 (" + ffmpegBin + ")，请安装 FFmpeg 后重试");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread drainer = new Thread(() -> {
            try (InputStream in = process.getInputStream()) {
                in.transferTo(output);
            } catch (IOException ignored) {}
        });
        drainer.start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            Files.deleteIfExists(temp);
            throw new IOException("FFmpeg 转码超时（超过 " + timeoutSeconds + " 秒）");
        }
        drainer.join(2000);
        if (process.exitValue() != 0 || !Files.isRegularFile(temp)) {
            Files.deleteIfExists(temp);
            throw new IOException("FFmpeg 转码失败，退出码: " + process.exitValue());
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        markReady(assetId, key, "video/mp4", Files.size(target));
    }

    private Optional<PreviewAsset> findAsset(long versionId, String assetType) {
        var rows = db.query(
                "SELECT id, file_id, version_id, asset_type, status, mime_type, size_bytes, error_message, attempt_count, updated_at "
                        + "FROM preview_asset WHERE version_id=? AND asset_type=?",
                (r, i) -> new PreviewAsset(
                        r.getString("id"),
                        r.getString("file_id"),
                        r.getString("version_id"),
                        r.getString("asset_type"),
                        r.getString("status"),
                        r.getString("mime_type"),
                        r.getLong("size_bytes"),
                        r.getString("error_message"),
                        r.getInt("attempt_count"),
                        r.getObject("updated_at", LocalDateTime.class).toInstant(ZoneOffset.UTC).toString()),
                versionId, assetType);
        return rows.stream().findFirst();
    }

    private void markReady(String assetId, String storageKey, String mimeType, long sizeBytes) {
        db.update(
                "UPDATE preview_asset SET status='READY', storage_key=?, mime_type=?, size_bytes=?, error_message=NULL, updated_at=UTC_TIMESTAMP(3) WHERE id=?",
                storageKey, mimeType, sizeBytes, Long.parseLong(assetId));
    }

    private void markFailed(String assetId, String message) {
        String truncated = message.length() > 950 ? message.substring(0, 950) : message;
        db.update(
                "UPDATE preview_asset SET status='FAILED', error_message=?, updated_at=UTC_TIMESTAMP(3) WHERE id=?",
                truncated, Long.parseLong(assetId));
    }

    private static String normalizeAssetType(String raw) {
        if (raw == null) throw new BizException(HttpStatus.BAD_REQUEST, 400, "不支持的派生资源类型");
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        if (!"OFFICE_PDF".equals(upper) && !"VIDEO_MP4".equals(upper)) {
            throw new BizException(HttpStatus.BAD_REQUEST, 400, "不支持的派生资源类型: " + raw);
        }
        return upper;
    }

    private static String extensionOf(String name) {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0; }
    }
}
