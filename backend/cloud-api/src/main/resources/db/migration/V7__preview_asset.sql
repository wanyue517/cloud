CREATE TABLE IF NOT EXISTS preview_asset (
    id BIGINT NOT NULL PRIMARY KEY,
    file_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    asset_type VARCHAR(32) NOT NULL COMMENT 'OFFICE_PDF | VIDEO_MP4',
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | PROCESSING | READY | FAILED',
    storage_key VARCHAR(255) NULL,
    mime_type VARCHAR(128) NULL,
    size_bytes BIGINT NOT NULL DEFAULT 0,
    metadata_json TEXT NULL,
    error_message VARCHAR(1000) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_preview_version_type (version_id, asset_type),
    KEY idx_preview_file_status (file_id, status),
    KEY idx_preview_status_updated (status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件预览派生资源及转换状态表';
