CREATE TABLE IF NOT EXISTS search_outbox (
    id BIGINT NOT NULL PRIMARY KEY,
    event_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'UPSERT | DELETE | REBUILD',
    space_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    version_id BIGINT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | PROCESSING | DONE | FAILED',
    attempt_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_search_outbox_status (status, created_at),
    KEY idx_search_outbox_node (space_id, node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全文索引异步同步 Outbox 事件表';

CREATE TABLE IF NOT EXISTS search_text_chunk (
    id BIGINT NOT NULL PRIMARY KEY,
    space_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    content MEDIUMTEXT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_search_chunk_version (node_id, version_id, chunk_index),
    KEY idx_search_chunk_space_node (space_id, node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='长文本分段正文索引表（每片关联 file/version）';
