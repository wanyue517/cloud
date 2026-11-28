package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.modules.drive.DriveModels.MediaSource;
import com.wanyue.cloud.modules.drive.storage.StorageRouterService;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.concurrent.*;

import static com.wanyue.cloud.modules.drive.preview.PreviewModels.VideoProbe;

/** Bounded, per-version probe cache. Restarting simply probes again on the next authorized request. */
@Service
public class VideoProbeService {
    private record Key(long version, long profile, String object) {}
    private static class Entry {
        final long created = System.nanoTime();
        volatile VideoProbe result = state("PENDING", "等待视频探测");
    }
    private final LinkedHashMap<Key, Entry> cache = new LinkedHashMap<>();
    private final StorageRouterService storage;
    private final PreviewAssetService assets;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), r -> { Thread t = new Thread(r, "video-probe"); t.setDaemon(true); return t; });

    public VideoProbeService(StorageRouterService storage, PreviewAssetService assets) { this.storage = storage; this.assets = assets; }

    public synchronized VideoProbe getOrRequest(MediaSource source) {
        Key key = new Key(source.versionId(), source.profileId(), source.storageKey());
        Entry existing = cache.get(key);
        if (existing != null && System.nanoTime() - existing.created < Duration.ofMinutes(
                "FAILED".equals(existing.result.status()) ? 1 : 60).toNanos()) return existing.result;
        cache.remove(key);
        if (cache.size() >= 256) cache.entrySet().removeIf(e -> !"PENDING".equals(e.getValue().result.status()) && !"PROCESSING".equals(e.getValue().result.status()));
        Entry entry = new Entry();
        cache.put(key, entry);
        try {
            executor.execute(() -> {
                entry.result = state("PROCESSING", "正在探测视频，原文件可先尝试播放");
                try { entry.result = assets.probeInput(storage.probeInput(source), source.name()); }
                catch (Exception e) { entry.result = state("FAILED", "视频探测失败，可稍后重试"); }
            });
        } catch (RejectedExecutionException e) {
            cache.remove(key);
            return state("FAILED", "视频探测队列繁忙，请稍后重试");
        }
        return entry.result;
    }

    private static VideoProbe state(String status, String reason) {
        return new VideoProbe("", "", "", 0, 0, 0, 0, "UNKNOWN", reason, status);
    }

    @PreDestroy public void close() { executor.shutdownNow(); }
}
