-- V17: 跨端阅读与播放进度接力（视频/音频播放秒数、PDF 页码；不落明文文件名以保护私密文件）

CREATE TABLE IF NOT EXISTS user_file_progress (
    id             BIGINT      NOT NULL PRIMARY KEY,
    user_id        BIGINT      NOT NULL,
    node_id        BIGINT      NOT NULL,
    progress_type  VARCHAR(16) NOT NULL,
    position_value DOUBLE      NOT NULL DEFAULT 0,
    updated_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_user_node_progress (user_id, node_id),
    KEY idx_progress_user_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
