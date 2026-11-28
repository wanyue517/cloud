ALTER TABLE upload_session
    ADD COLUMN completion_requested_at DATETIME(3) NULL,
    ADD COLUMN completion_sha256 CHAR(64) NULL,
    ADD COLUMN completion_error VARCHAR(500) NULL,
    ADD COLUMN completion_private_token TEXT NULL,
    ADD KEY idx_upload_completion (status, completion_requested_at);
