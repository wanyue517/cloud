-- V16: 后台错误上报记录表与系统图片资源表（使用已配置存储方案）

CREATE TABLE IF NOT EXISTS sys_error_report (
    id              BIGINT        NOT NULL PRIMARY KEY,
    trace_id        VARCHAR(64)   NOT NULL DEFAULT '',
    level           VARCHAR(16)   NOT NULL DEFAULT 'ERROR',
    http_status     INT           NOT NULL DEFAULT 500,
    error_code      INT           NOT NULL DEFAULT 500,
    method          VARCHAR(16)   NOT NULL DEFAULT '',
    path            VARCHAR(255)  NOT NULL DEFAULT '',
    user_id         BIGINT        NULL,
    exception_class VARCHAR(255)  NOT NULL DEFAULT '',
    message         VARCHAR(1000) NOT NULL DEFAULT '',
    stack_trace     TEXT          NULL,
    resolved        TINYINT(1)    NOT NULL DEFAULT 0,
    resolved_at     DATETIME(3)   NULL,
    created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_error_report_created (created_at),
    KEY idx_error_report_resolved (resolved, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_asset (
    id                 BIGINT       NOT NULL PRIMARY KEY,
    category           VARCHAR(32)  NOT NULL DEFAULT 'general',
    storage_profile_id BIGINT       NOT NULL DEFAULT 1,
    storage_key        VARCHAR(512) NOT NULL,
    original_name      VARCHAR(255) NOT NULL DEFAULT '',
    mime_type          VARCHAR(128) NOT NULL DEFAULT 'application/octet-stream',
    size_bytes         BIGINT       NOT NULL DEFAULT 0,
    uploaded_by        BIGINT       NULL,
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_sys_asset_category (category, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
