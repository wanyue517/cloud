ALTER TABLE storage_profile
    ADD COLUMN playback_mode VARCHAR(8) NOT NULL DEFAULT 'AUTO',
    ADD COLUMN playback_endpoint VARCHAR(512) NULL,
    ADD CONSTRAINT ck_storage_playback_mode CHECK (playback_mode IN ('AUTO','DIRECT','PROXY'));
