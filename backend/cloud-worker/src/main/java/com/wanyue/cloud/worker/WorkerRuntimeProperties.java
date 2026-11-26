package com.wanyue.cloud.worker;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Worker 进程运行配置。后续预览转换、全文索引与存储清理消费者复用此命名空间。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "cloud.worker")
public class WorkerRuntimeProperties {

    private boolean enabled = true;
    private String nodeId = "worker-local-1";
    private long heartbeatIntervalMs = 30000L;
}
