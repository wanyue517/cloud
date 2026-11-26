ALTER TABLE storage_profile
    ADD COLUMN endpoint VARCHAR(512) NULL AFTER root_path,
    ADD COLUMN bucket VARCHAR(128) NULL AFTER endpoint,
    ADD COLUMN region VARCHAR(64) NULL AFTER bucket,
    ADD COLUMN access_key VARCHAR(255) NULL AFTER region,
    ADD COLUMN secret_cipher TEXT NULL COMMENT 'AES-GCM 加密后的密钥，绝不明文回显' AFTER access_key,
    ADD COLUMN max_file_bytes BIGINT NULL COMMENT 'DB 存储适配器等单文件上限' AFTER secret_cipher;

CREATE TABLE IF NOT EXISTS blob_db_content (
    storage_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content LONGBLOB NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据库小文件存储表（限额校验）';

CREATE TABLE IF NOT EXISTS sys_oauth_provider (
    provider_code VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    display_name VARCHAR(64) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    client_id VARCHAR(255) NOT NULL DEFAULT '',
    client_secret_cipher TEXT NULL,
    authorize_url VARCHAR(512) NOT NULL DEFAULT '',
    token_url VARCHAR(512) NOT NULL DEFAULT '',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='第三方身份提供商可扩展配置表';

INSERT IGNORE INTO sys_oauth_provider(provider_code, display_name, enabled, authorize_url, token_url) VALUES
    ('github', 'GitHub', 0, 'https://github.com/login/oauth/authorize', 'https://github.com/login/oauth/access_token'),
    ('oidc', '通用 OIDC', 0, '', '');

INSERT IGNORE INTO sys_policy(policy_key, policy_value) VALUES
    ('site_name', 'Cloudlane'),
    ('site_logo_url', ''),
    ('site_announcement', ''),
    ('auth_allow_register', 'true'),
    ('auth_require_email_verify', 'true'),
    ('auth_allow_password_login', 'true'),
    ('smtp_host', ''),
    ('smtp_port', '587'),
    ('smtp_username', ''),
    ('smtp_from', ''),
    ('smtp_password_cipher', '');
