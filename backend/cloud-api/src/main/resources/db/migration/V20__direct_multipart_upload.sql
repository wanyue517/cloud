ALTER TABLE storage_profile
 ADD COLUMN upload_mode VARCHAR(16) NOT NULL DEFAULT 'PROXY',
 ADD COLUMN direct_threshold_bytes BIGINT NOT NULL DEFAULT 104857600,
 ADD COLUMN direct_chunk_size INT NOT NULL DEFAULT 16777216,
 ADD COLUMN direct_concurrency INT NOT NULL DEFAULT 3,
 ADD COLUMN direct_url_ttl INT NOT NULL DEFAULT 900,
 ADD COLUMN direct_fallback BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE upload_session
 ADD COLUMN upload_mode VARCHAR(16) NOT NULL DEFAULT 'PROXY',
 ADD COLUMN direct_key VARCHAR(128) NULL,
 ADD COLUMN direct_upload_id VARCHAR(1024) NULL,
 ADD COLUMN direct_concurrency INT NOT NULL DEFAULT 3,
 ADD COLUMN direct_url_ttl INT NOT NULL DEFAULT 900,
 ADD COLUMN direct_fallback BOOLEAN NOT NULL DEFAULT FALSE;

-- Unknown content hashes must never be treated as trusted client-provided hashes for instant upload.
ALTER TABLE blob_object MODIFY COLUMN sha256 CHAR(64) NULL;
