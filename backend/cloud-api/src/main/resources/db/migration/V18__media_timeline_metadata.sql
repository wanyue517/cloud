-- V18: 媒体 EXIF 拍摄时间与分辨率元数据表（支撑云端相册时光轴分页查询）

CREATE TABLE IF NOT EXISTS media_metadata (
    version_id       BIGINT      NOT NULL PRIMARY KEY,
    node_id          BIGINT      NOT NULL,
    space_id         BIGINT      NOT NULL,
    media_type       VARCHAR(16) NOT NULL,
    taken_at         DATETIME(3) NULL,
    width            INT         NULL,
    height           INT         NULL,
    duration_seconds INT         NULL,
    updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_media_meta_node (node_id),
    KEY idx_media_meta_space_taken (space_id, taken_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
