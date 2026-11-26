CREATE TABLE IF NOT EXISTS private_space_session (
    id BIGINT NOT NULL PRIMARY KEY,
    space_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idle_timeout_minutes INT NOT NULL DEFAULT 15,
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_private_session_token (token_hash),
    KEY idx_private_session_user_space (user_id, space_id, revoked_at, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='私密空间短期解锁授权会话表';

CREATE TABLE IF NOT EXISTS user_recent_access (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    space_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    accessed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_recent_user_node (user_id, node_id),
    KEY idx_recent_user_time (user_id, accessed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户最近使用文件记录表';

CREATE TABLE IF NOT EXISTS batch_job (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    space_id BIGINT NOT NULL,
    job_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'MOVE | COPY | DELETE | ZIP_DOWNLOAD',
    status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | RUNNING | COMPLETED | PARTIAL | FAILED',
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    result_json MEDIUMTEXT NULL,
    artifact_path VARCHAR(512) NULL,
    error_message VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_batch_job_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='批量操作与异步打包下载任务表';
