package com.wanyue.cloud.worker;

import com.wanyue.cloud.common.result.R;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "cloud.worker.node-id=worker-test-1",
        "cloud.worker.enabled=true"
})
class CloudWorkerApplicationTests {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void storagePaths(DynamicPropertyRegistry registry) {
        registry.add("cloud.storage.root", () -> tempDir.resolve("objects").toString());
        registry.add("cloud.storage.preview-root", () -> tempDir.resolve("previews").toString());
        registry.add("cloud.storage.worker-tmp", () -> tempDir.resolve("worker-tmp").toString());
    }

    @Autowired
    ApplicationContext context;

    @Autowired
    WorkerLifecycleRunner runner;

    @Autowired
    WorkerRuntimeProperties properties;

    @Test
    void workerStartsIndependentlyWithoutWebServerAndInitializesLocalDirectories() {
        assertFalse(context.containsBean("dispatcherServlet"), "Worker 不应启动 Web MVC 容器");
        assertTrue(runner.isReady());
        assertEquals("worker-test-1", properties.getNodeId());
        assertTrue(Files.isDirectory(runner.getStorageRoot()));
        assertTrue(Files.isDirectory(runner.getPreviewRoot()));
        assertTrue(Files.isDirectory(runner.getWorkerTmp()));
        R<String> status = runner.status();
        assertEquals(R.SUCCESS_CODE, status.getCode());
        assertEquals("worker-test-1", status.getData());
    }
}
