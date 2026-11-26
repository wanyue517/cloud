-- V15: 动态系统策略配置与上传会话存储实例绑定（需求 4.7 / 6.5 / 7.3 / 21.2 / 24.2）

ALTER TABLE upload_session
    ADD COLUMN storage_profile_id BIGINT NOT NULL DEFAULT 1 AFTER space_id;

INSERT IGNORE INTO sys_policy(policy_key, policy_value) VALUES
    ('upload_chunk_size_mb', '4'),
    ('upload_max_file_mb', '10240'),
    ('upload_max_concurrent_sessions', '20'),
    ('upload_session_ttl_hours', '24'),
    ('user_default_quota_gb', '100'),
    ('max_directory_depth', '256'),
    ('trash_retention_days', '30'),
    ('blocked_extensions', ''),
    ('auth_max_login_failures', '5'),
    ('auth_lock_minutes', '15');
