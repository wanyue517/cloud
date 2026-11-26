package com.wanyue.cloud.worker;

import com.wanyue.cloud.common.result.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Worker 启动生命周期记录、本地工作目录初始化与健康状态标记。
 */
@Component
public class WorkerLifecycleRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkerLifecycleRunner.class);
    private final WorkerRuntimeProperties properties;
    private final Path storageRoot;
    private final Path previewRoot;
    private final Path workerTmp;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    public WorkerLifecycleRunner(
            WorkerRuntimeProperties properties,
            @Value("${cloud.storage.root:data/objects}") String storageRoot,
            @Value("${cloud.storage.preview-root:data/previews}") String previewRoot,
            @Value("${cloud.storage.worker-tmp:data/worker-tmp}") String workerTmp) {
        this.properties = properties;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.previewRoot = Path.of(previewRoot).toAbsolutePath().normalize();
        this.workerTmp = Path.of(workerTmp).toAbsolutePath().normalize();
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        Files.createDirectories(storageRoot);
        Files.createDirectories(previewRoot);
        Files.createDirectories(workerTmp);
        ready.set(properties.isEnabled());
        log.info("Cloudlane Worker started: nodeId={}, enabled={}, storageRoot={}, previewRoot={}, workerTmp={}",
                properties.getNodeId(), properties.isEnabled(), storageRoot, previewRoot, workerTmp);
    }

    public boolean isReady() {
        return ready.get();
    }

    public Path getStorageRoot() {
        return storageRoot;
    }

    public Path getPreviewRoot() {
        return previewRoot;
    }

    public Path getWorkerTmp() {
        return workerTmp;
    }

    public R<String> status() {
        return ready.get()
                ? R.ok(properties.getNodeId())
                : R.fail("Worker 未启用");
    }
}
