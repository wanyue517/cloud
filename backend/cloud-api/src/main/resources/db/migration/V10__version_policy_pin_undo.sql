CREATE TABLE IF NOT EXISTS sys_policy (
    policy_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    policy_value VARCHAR(255) NOT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统级策略配置表';

INSERT IGNORE INTO sys_policy(policy_key, policy_value) VALUES ('max_file_versions', '20');

CREATE TABLE IF NOT EXISTS user_version_policy (
    user_id BIGINT NOT NULL PRIMARY KEY,
    max_versions INT NOT NULL DEFAULT 10,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户个人历史版本保留上限';

CREATE TABLE IF NOT EXISTS user_quick_access (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    space_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_quick_access_user_node (user_id, node_id),
    KEY idx_quick_access_user_sort (user_id, sort_order, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户快捷访问固定引用表（不复制物理文件）';

CREATE TABLE IF NOT EXISTS undo_token (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    space_id BIGINT NOT NULL,
    action_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'RENAME | MOVE | TRASH | BATCH_RENAME',
    summary VARCHAR(255) NOT NULL DEFAULT '',
    payload_json TEXT NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    consumed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_undo_user_expires (user_id, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='10秒操作撤销令牌表';
