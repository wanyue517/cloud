CREATE TABLE live_photo_pair (
    photo_version_id BIGINT NOT NULL PRIMARY KEY,
    video_node_id BIGINT NOT NULL,
    video_version_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
