-- Logical nodes, immutable content versions and physical storage objects stay independent.
CREATE TABLE storage_profile (
 id BIGINT PRIMARY KEY, name VARCHAR(100) NOT NULL, provider VARCHAR(20) NOT NULL,
 root_path VARCHAR(1024) NOT NULL, is_default BOOLEAN NOT NULL DEFAULT FALSE
);
INSERT INTO storage_profile VALUES (1, '本地存储', 'LOCAL', 'data/objects', TRUE);
CREATE TABLE blob_object (
 id BIGINT PRIMARY KEY, storage_profile_id BIGINT NOT NULL, storage_key VARCHAR(128) NOT NULL,
 size_bytes BIGINT NOT NULL, sha256 CHAR(64) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_blob_key (storage_profile_id, storage_key)
);
CREATE TABLE file_node (
 id BIGINT PRIMARY KEY, space_id BIGINT NOT NULL, parent_id BIGINT NOT NULL DEFAULT 0,
 node_type VARCHAR(16) NOT NULL, name VARCHAR(255) NOT NULL,
 current_version_id BIGINT NULL, hidden BOOLEAN NOT NULL DEFAULT FALSE,
 revision BIGINT NOT NULL DEFAULT 0, deleted_batch BIGINT NOT NULL DEFAULT 0,
 deleted_at DATETIME(3) NULL, created_by BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 active_name VARCHAR(255) GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN name ELSE NULL END) VIRTUAL,
 UNIQUE KEY uk_node_name (space_id, parent_id, active_name),
 KEY idx_node_parent (space_id,parent_id,deleted_at,id),
 KEY idx_node_deleted (space_id,deleted_at),
 CHECK (node_type IN ('FILE','FOLDER')), CHECK (id > 0 AND parent_id >= 0)
);
CREATE TABLE file_version (
 id BIGINT PRIMARY KEY, file_id BIGINT NOT NULL, blob_id BIGINT NOT NULL,
 version_number INT NOT NULL, mime_type VARCHAR(255) NOT NULL,
 created_by BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_file_version (file_id, version_number)
);
CREATE TABLE upload_session (
 id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, space_id BIGINT NOT NULL,
 parent_id BIGINT NOT NULL, name VARCHAR(255) NOT NULL, size_bytes BIGINT NOT NULL,
 chunk_size INT NOT NULL, chunk_count INT NOT NULL, mime_type VARCHAR(255) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'UPLOADING', file_id BIGINT NULL,
 expires_at DATETIME(3) NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_upload_expiry (status,expires_at)
);
CREATE TABLE file_favorite (
 user_id BIGINT NOT NULL, file_id BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (user_id,file_id)
);
ALTER TABLE space ADD COLUMN sort_order BIGINT NOT NULL DEFAULT 0;
