package com.wanyue.cloud.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 独立异步任务 Worker 启动入口（非 Web 容器，可与 cloud-api 独立部署与扩缩容）。
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(WorkerRuntimeProperties.class)
public class CloudWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudWorkerApplication.class, args);
    }
}
