package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.drive.DriveModels.MediaSource;
import com.wanyue.cloud.modules.drive.storage.StorageRouterService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Produces an EVENT playlist atomically while FFmpeg is still reading/encoding the source. */
@Service
public class VideoStreamingService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(VideoStreamingService.class);
    public record State(String status, double availableSeconds, String errorMessage) {}
    private static final String FORMATS = "mov,matroska,webm,avi,mpegts,mpeg,mpegvideo,flv,ogg,asf,mxf,rm,dv,h264,hevc,av1,m4v,nut,h263,mjpeg";
    private static class Job {
        volatile String status = "PENDING";
        volatile String error;
        volatile Process process;
    }
    private final Path root;
    private final String ffmpeg;
    private final StorageRouterService storage;
    private final PreviewAssetService assets;
    private final ConcurrentHashMap<Long, Job> jobs = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor;
    private final long timeoutSeconds;

    public VideoStreamingService(StorageRouterService storage, PreviewAssetService assets,
            @Value("${cloud.storage.preview-root:data/previews}") String previewRoot,
            @Value("${cloud.preview.ffmpeg-bin:ffmpeg}") String ffmpeg,
            @Value("${cloud.preview.max-concurrency:2}") int concurrency,
            @Value("${cloud.preview.streaming-timeout-seconds:21600}") long timeoutSeconds) throws IOException {
        this.storage = storage;
        this.assets = assets;
        this.ffmpeg = ffmpeg;
        this.timeoutSeconds = timeoutSeconds;
        root = Path.of(previewRoot).toAbsolutePath().normalize().resolve("hls");
        Files.createDirectories(root);
        int slots = Math.max(1, concurrency);
        executor = new ThreadPoolExecutor(slots, slots, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16),
                r -> { var t = new Thread(r, "video-streaming"); t.setDaemon(true); return t; });
    }

    public synchronized State getOrStart(MediaSource source, boolean retry) {
        Job job = jobs.get(source.versionId());
        if (job != null && "READY".equals(job.status) && !complete(source.versionId())) {
            jobs.remove(source.versionId(), job);
            job = null;
        }
        if (job == null && complete(source.versionId())) return state(source.versionId(), "READY", null);
        if (job == null || (retry && "FAILED".equals(job.status))) {
            if (jobs.size() >= 256) jobs.entrySet().removeIf(e -> Set.of("READY", "FAILED").contains(e.getValue().status));
            job = new Job();
            jobs.put(source.versionId(), job);
            Job submitted = job;
            try { executor.execute(() -> produce(source, submitted)); }
            catch (RejectedExecutionException busy) {
                jobs.remove(source.versionId(), job);
                throw new BizException(HttpStatus.SERVICE_UNAVAILABLE, 503, "视频处理队列繁忙，请稍后重试");
            }
        }
        return state(source.versionId(), job.status, job.error);
    }

    public boolean hasPlayable(long version) {
        Job job = jobs.get(version);
        return (job == null ? complete(version) : !"FAILED".equals(job.status)) && available(version) > 0;
    }

    public State status(long version) {
        Job job = jobs.get(version);
        return state(version, job == null ? (complete(version) ? "READY" : "FAILED") : job.status,
                job == null && !complete(version) ? "视频处理已中断，请重试" : job == null ? null : job.error);
    }

    private State state(long version, String status, String error) {
        double seconds = available(version);
        if ("PROCESSING".equals(status) && seconds > 0) status = "PLAYABLE";
        return new State(status, seconds, error);
    }

    private Path directory(long version) {
        if (version <= 0) throw new IllegalArgumentException("invalid version");
        return root.resolve(Long.toString(version));
    }

    public Path file(long version, String file) {
        if (!file.matches("index\\.m3u8|segment-[0-9]{6,}\\.ts")) throw BizException.notFound("视频分段不存在");
        Path path = directory(version).resolve(file);
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) throw BizException.notFound("视频分段尚未生成");
        return path;
    }

    private boolean complete(long version) {
        try { return Files.isRegularFile(directory(version).resolve("complete"))
                && Files.readString(file(version, "index.m3u8")).contains("#EXT-X-ENDLIST") && available(version) > 0; }
        catch (Exception missing) { return false; }
    }

    private double available(long version) {
        try {
            double seconds = 0;
            for (String line : Files.readAllLines(file(version, "index.m3u8"))) {
                if (line.startsWith("#EXTINF:")) seconds += Double.parseDouble(line.substring(8).split(",", 2)[0]);
                if (!line.isBlank() && !line.startsWith("#") && Files.size(file(version, line)) <= 0) return 0;
            }
            return seconds;
        } catch (Exception missing) { return 0; }
    }

    public Optional<Path> thumbnailInput(long version) {
        if (!hasPlayable(version)) return Optional.empty();
        try {
            return Files.readAllLines(file(version, "index.m3u8")).stream()
                    .filter(line -> line.matches("segment-[0-9]{6,}\\.ts")).findFirst().map(line -> file(version, line));
        } catch (Exception missing) { return Optional.empty(); }
    }

    private void produce(MediaSource source, Job job) {
        job.status = "PROCESSING";
        Process process = null;
        StreamingInputProxy inputProxy = null;
        try {
            Path dir = directory(source.versionId());
            Files.createDirectories(dir);
            Files.deleteIfExists(dir.resolve("complete"));
            // A restart/retry rebuilds an incomplete cache, never user source objects.
            try (var entries = Files.list(dir)) {
                for (Path entry : entries.toList()) {
                    if (entry.getFileName().toString().matches("index\\.m3u8(\\.tmp)?|segment-[0-9]{6,}\\.ts(\\.tmp)?"))
                        Files.deleteIfExists(entry);
                }
            }
            boolean remote = storage.isRemote(source.profileId());
            boolean fragmented = false;
            PreviewModels.VideoProbe headerProbe = null;
            if (remote) {
                inputProxy = new StreamingInputProxy(storage, source, dir.resolve("input"));
                var metadata = inputProxy.mp4Metadata();
                if (metadata.isPresent()) {
                    fragmented = VideoContainerInspector.fragmented(metadata.get());
                    headerProbe = VideoContainerInspector.mp4Probe(metadata.get());
                }
            } else if (source.size() >= 8) {
                try (var prefix = storage.openRange(source, 0, Math.min(source.size(), 65536) - 1)) {
                    fragmented = VideoContainerInspector.fragmented(prefix.readNBytes(65536));
                }
            }
            String input = remote ? inputProxy.url() : storage.probeInput(source);
            var probe = headerProbe != null ? headerProbe : assets.probeStreamingInput(input, source.name(), fragmented);
            List<String> cmd = new ArrayList<>(List.of(ffmpeg, "-y", "-nostdin", "-hide_banner", "-loglevel", "error"));
            cmd.addAll(List.of("-protocol_whitelist", remote ? "http,https,tcp,tls,httpproxy" : "file",
                    "-format_whitelist", FORMATS));
            if (remote) cmd.addAll(List.of("-rw_timeout", "15000000", "-seekable", fragmented ? "0" : "1",
                    "-multiple_requests", "0", "-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "2"));
            if (headerProbe != null) cmd.addAll(List.of("-fflags", "+ignidx", "-ignore_chapters", "1", "-nofind_stream_info"));
            cmd.addAll(List.of("-probesize", "1048576", "-analyzeduration", "1000000", "-i", input));
            var encoding = new ArrayList<>(PreviewAssetService.compatibilityArguments(probe));
            encoding.subList(encoding.size() - 2, encoding.size()).clear(); // faststart applies to complete MP4, not HLS.
            cmd.addAll(encoding);
            if (probe.videoTranscodeRequired()) cmd.addAll(List.of("-force_key_frames", "expr:gte(t,n_forced*4)", "-sc_threshold", "0"));
            cmd.addAll(List.of("-f", "hls", "-hls_time", "4", "-hls_list_size", "0", "-hls_playlist_type", "event",
                    "-hls_flags", "independent_segments+temp_file", "-hls_segment_filename", dir.resolve("segment-%06d.ts").toString(),
                    dir.resolve("index.m3u8").toString()));
            process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            job.process = process;
            Process running = process;
            var diagnostics = new java.io.ByteArrayOutputStream();
            Thread drain = new Thread(() -> {
                try (var out = running.getInputStream()) {
                    diagnostics.write(out.readNBytes(16384));
                    out.transferTo(OutputStream.nullOutputStream());
                }
                catch (IOException ignored) {}
            }, "video-streaming-output");
            drain.setDaemon(true);
            drain.start();
            long started = System.nanoTime();
            long lastProgress = started;
            double lastSeconds = 0;
            while (!process.waitFor(1, TimeUnit.SECONDS)) {
                double seconds = available(source.versionId());
                if (seconds > lastSeconds) { lastSeconds = seconds; lastProgress = System.nanoTime(); }
                if (System.nanoTime() - lastProgress > Duration.ofMinutes(2).toNanos()
                        || System.nanoTime() - started > TimeUnit.SECONDS.toNanos(timeoutSeconds))
                    throw new IOException("视频处理超时，请检查存储网络后重试");
            }
            drain.join(1000);
            if (diagnostics.size() > 0) log.warn("Video processing diagnostics version={}: {}", source.versionId(),
                    diagnostics.toString(StandardCharsets.UTF_8).replaceAll("https?://\\S+", "[video input]"));
            if (process.exitValue() != 0 || !Files.readString(file(source.versionId(), "index.m3u8")).contains("#EXT-X-ENDLIST")
                    || available(source.versionId()) <= 0)
                throw new IOException("视频流转换失败，请检查源文件完整性、编码与存储网络");
            if (probe.durationSeconds() > 0 && Math.abs(available(source.versionId()) - probe.durationSeconds()) > Math.max(3, probe.durationSeconds() * .01))
                throw new IOException("视频处理结果不完整");
            Files.writeString(dir.resolve("complete"), "complete", StandardCharsets.UTF_8);
            job.status = "READY";
        } catch (Exception failure) {
            log.warn("Video processing failed version={} type={}", source.versionId(), failure.getClass().getSimpleName());
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            job.error = failure instanceof IOException ? "视频处理失败或超时，请重试" : "视频处理失败，请重试";
            job.status = "FAILED";
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (inputProxy != null) inputProxy.close();
            job.process = null;
        }
    }

    @PreDestroy public void close() {
        executor.shutdownNow();
        jobs.values().forEach(job -> { if (job.process != null) job.process.destroyForcibly(); });
    }
}
