-- V14: 独立 Worker 异步作业与死信队列表、请求链路审计日志表（阶段 14: T165 / T166 / T173）

CREATE TABLE IF NOT EXISTS async_job (
    id              BIGINT       NOT NULL PRIMARY KEY,
    consumer_group  VARCHAR(64)  NOT NULL,
    job_type        VARCHAR(64)  NOT NULL,
    payload         TEXT         NULL,
    status          VARCHAR(24)  NOT NULL DEFAULT 'PENDING',
    retry_count     INT          NOT NULL DEFAULT 0,
    max_retries     INT          NOT NULL DEFAULT 3,
    next_retry_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_error      VARCHAR(1000) NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_async_job_group_status (consumer_group, status, next_retry_at),
    KEY idx_async_job_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_audit_log (
    id           BIGINT       NOT NULL PRIMARY KEY,
    trace_id     VARCHAR(64)  NOT NULL,
    user_id      BIGINT       NULL,
    method       VARCHAR(16)  NOT NULL,
    path         VARCHAR(255) NOT NULL,
    status_code  INT          NOT NULL,
    duration_ms  BIGINT       NOT NULL DEFAULT 0,
    client_ip    VARCHAR(64)  NULL,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_audit_created (created_at),
    KEY idx_audit_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
