CREATE TABLE IF NOT EXISTS share_link (
    id BIGINT NOT NULL PRIMARY KEY,
    space_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    creator_user_id BIGINT NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '高熵分享令牌 SHA-256 摘要，绝不存明文',
    token_prefix VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT '',
    password_hash VARCHAR(128) NULL COMMENT '提取码 BCrypt 哈希，可空',
    allow_download TINYINT(1) NOT NULL DEFAULT 1,
    max_downloads INT NULL,
    download_count INT NOT NULL DEFAULT 0,
    view_count INT NOT NULL DEFAULT 0,
    expires_at DATETIME(3) NULL,
    disabled_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_share_link_token_hash (token_hash),
    KEY idx_share_link_creator (creator_user_id, created_at),
    KEY idx_share_link_node (space_id, node_id, disabled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公开分享链接表（仅存令牌摘要）';

CREATE TABLE IF NOT EXISTS sys_message (
    id BIGINT NOT NULL PRIMARY KEY,
    sender_user_id BIGINT NOT NULL DEFAULT 0 COMMENT '0 表示系统',
    scope VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DIRECT' COMMENT 'ALL | DIRECT',
    category VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'SYSTEM' COMMENT 'SYSTEM | SHARE_INVITE | SHARE_EVENT | SECURITY',
    title VARCHAR(160) NOT NULL,
    content VARCHAR(2000) NOT NULL DEFAULT '',
    action_url VARCHAR(255) NOT NULL DEFAULT '',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_sys_message_scope_created (scope, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统与个人通知消息主表';

CREATE TABLE IF NOT EXISTS sys_message_receipt (
    id BIGINT NOT NULL PRIMARY KEY,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    read_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_message_receipt_user (message_id, user_id),
    KEY idx_message_receipt_inbox (user_id, is_read, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户消息接收与已读状态表';
