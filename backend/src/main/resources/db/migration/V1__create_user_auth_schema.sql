-- Cloudlane 用户与认证数据库架构
-- 基准：MySQL 8.4 / Java 17 / Spring Boot 4.2.0 / Flyway
-- 说明：所有业务表包含 7 个 BaseDO 字段，时间统一按 UTC 存储。
-- 逻辑删除：deleted=0 表示有效，已删除时 deleted=id。
-- 唯一约束：业务唯一键末尾附加 deleted，支持逻辑删除后重建。
-- 禁止在已执行的 Flyway 迁移脚本上直接编辑。
CREATE TABLE `sys_user` (
    `id` BIGINT NOT NULL COMMENT '全局唯一雪花ID',
    `display_name` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '用户显示名称',
    `avatar_url` VARCHAR(512) DEFAULT NULL COMMENT '用户头像资源地址',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '用户状态：0禁用，1正常，2冻结',
    `register_source` VARCHAR(24) NOT NULL DEFAULT 'PASSWORD' COMMENT '注册来源：PASSWORD/OAUTH/INVITE等',
    `registered_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '账号注册时间（UTC）',
    `last_login_at` DATETIME(3) DEFAULT NULL COMMENT '最近成功登录时间（UTC）',
    `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID；0表示系统',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人ID；0表示系统',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间（UTC）',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常；已删除为本行ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_sys_user_status_deleted` (`status`, `deleted`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='系统用户主表';

CREATE TABLE `sys_user_profile` (
    `id` BIGINT NOT NULL COMMENT '全局唯一雪花ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID，关联sys_user.id',
    `bio` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '个人简介',
    `website_url` VARCHAR(512) DEFAULT NULL COMMENT '个人网站地址',
    `cover_url` VARCHAR(512) DEFAULT NULL COMMENT '个人主页封面地址',
    `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID；0表示系统',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人ID；0表示系统',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间（UTC）',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常；已删除为本行ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_profile_user_deleted` (`user_id`, `deleted`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户扩展资料表';

CREATE TABLE `sys_user_setting` (
    `id` BIGINT NOT NULL COMMENT '全局唯一雪花ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID，关联sys_user.id',
    `theme` VARCHAR(16) NOT NULL DEFAULT 'system' COMMENT '主题：system/light/dark',
    `locale` VARCHAR(16) NOT NULL DEFAULT 'zh-CN' COMMENT '界面语言，如zh-CN、en-US',
    `timezone` VARCHAR(64) NOT NULL DEFAULT 'UTC' COMMENT 'IANA时区，如Asia/Shanghai',
    `density` VARCHAR(16) NOT NULL DEFAULT 'comfortable' COMMENT '显示密度：comfortable/compact',
    `file_view` VARCHAR(16) NOT NULL DEFAULT 'list' COMMENT '文件视图：list/grid',
    `file_sort` VARCHAR(32) NOT NULL DEFAULT 'name_asc' COMMENT '文件默认排序规则',
    `show_hidden_files` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否展示隐藏文件：0否，1是',
    `default_space_id` BIGINT DEFAULT NULL COMMENT '默认进入的空间ID，空间删除后需清空',
    `notification_settings` JSON DEFAULT NULL COMMENT '消息通知偏好JSON；不存放密钥',
    `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID；0表示系统',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人ID；0表示系统',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间（UTC）',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常；已删除为本行ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_setting_user_deleted` (`user_id`, `deleted`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户个性化设置表';

CREATE TABLE `auth_identity` (
    `id` BIGINT NOT NULL COMMENT '全局唯一雪花ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID，关联sys_user.id',
    `identity_type` VARCHAR(16) NOT NULL COMMENT '标识类型：USERNAME/EMAIL/PHONE',
    `identifier` VARCHAR(191) COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '规范化后的登录标识；用户名和邮箱不区分大小写，手机为E.164',
    `is_primary` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为该类型的首选标识',
    `verified_at` DATETIME(3) DEFAULT NULL COMMENT '身份验证完成时间（UTC）；NULL为未验证',
    `last_used_at` DATETIME(3) DEFAULT NULL COMMENT '最近作为登录标识使用的时间（UTC）',
    `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID；0表示系统',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人ID；0表示系统',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间（UTC）',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常；已删除为本行ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_identity_key_deleted` (`identity_type`, `identifier`, `deleted`),
    KEY `idx_identity_user_type_deleted` (`user_id`, `identity_type`, `deleted`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户登录标识表：用户名、邮箱、手机号';

CREATE TABLE `auth_password_credential` (
    `id` BIGINT NOT NULL COMMENT '全局唯一雪花ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID，关联sys_user.id',
    `password_hash` VARCHAR(255) NOT NULL COMMENT 'PasswordEncoder生成的密码哈希；禁止存储明文',
    `password_changed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近一次修改密码的时间（UTC）',
    `reset_required` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '下次登录是否强制重置密码',
    `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID；0表示系统',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人ID；0表示系统',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间（UTC）',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常；已删除为本行ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_password_user_deleted` (`user_id`, `deleted`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户密码凭证表：每用户最多一份有效密码';

CREATE TABLE `auth_oauth_account` (
    `id` BIGINT NOT NULL COMMENT '全局唯一雪花ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID，关联sys_user.id',
    `provider` VARCHAR(32) NOT NULL COMMENT '第三方平台编码：github/google/wechat等',
    `provider_app_id` VARCHAR(96) NOT NULL DEFAULT '' COMMENT '第三方应用标识；区分微信等多应用场景',
    `provider_subject` VARCHAR(191) COLLATE utf8mb4_bin NOT NULL COMMENT '第三方用户唯一标识，大小写敏感',
    `provider_union_id` VARCHAR(191) DEFAULT NULL COMMENT '平台跨应用联合标识，如微信UnionID',
    `provider_name` VARCHAR(128) DEFAULT NULL COMMENT '绑定时第三方账号显示名称',
    `provider_avatar_url` VARCHAR(512) DEFAULT NULL COMMENT '第三方账号头像地址',
    `last_used_at` DATETIME(3) DEFAULT NULL COMMENT '最近使用该第三方账号登录的时间（UTC）',
    `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID；0表示系统',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人ID；0表示系统',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间（UTC）',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常；已删除为本行ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oauth_account_deleted` (`provider`, `provider_app_id`, `provider_subject`, `deleted`),
    KEY `idx_oauth_user_deleted` (`user_id`, `deleted`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='第三方OAuth账号绑定表';

CREATE TABLE `auth_user_security` (
    `id` BIGINT NOT NULL COMMENT '全局唯一雪花ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID，关联sys_user.id',
    `failed_login_count` INT NOT NULL DEFAULT 0 COMMENT '连续密码登录失败次数',
    `locked_until` DATETIME(3) DEFAULT NULL COMMENT '临时登录锁定截止时间（UTC）',
    `password_login_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许密码登录：0否，1是',
    `session_version` INT NOT NULL DEFAULT 0 COMMENT '会话版本；递增可使旧令牌失效',
    `mfa_required` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否强制使用多因素认证',
    `last_password_login_at` DATETIME(3) DEFAULT NULL COMMENT '最后一次成功密码登录时间（UTC）',
    `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID；0表示系统',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人ID；0表示系统',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间（UTC）',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常；已删除为本行ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_security_user_deleted` (`user_id`, `deleted`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户登录安全状态表';

CREATE TABLE `auth_session` (
    `id` BIGINT NOT NULL COMMENT '全局唯一雪花ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID，关联sys_user.id',
    `refresh_token_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'Refresh Token的HMAC-SHA256十六进制摘要；禁止存明文',
    `refresh_expires_at` DATETIME(3) NOT NULL COMMENT 'Refresh Token到期时间（UTC）',
    `device_id` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '客户端生成的设备标识；不作为安全认证凭据',
    `device_name` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '设备显示名称',
    `client_type` VARCHAR(24) NOT NULL DEFAULT 'WEB' COMMENT '客户端类型：WEB/DESKTOP/ANDROID/IOS等',
    `user_agent` VARCHAR(512) DEFAULT NULL COMMENT '最近会话客户端User-Agent',
    `ip_address` VARCHAR(45) DEFAULT NULL COMMENT '最近使用的IPv4或IPv6地址',
    `last_used_at` DATETIME(3) DEFAULT NULL COMMENT '最近使用会话的时间（UTC）',
    `last_rotated_at` DATETIME(3) DEFAULT NULL COMMENT '最近轮换Refresh Token的时间（UTC）',
    `rotation_count` INT NOT NULL DEFAULT 0 COMMENT 'Refresh Token轮换次数',
    `session_version` INT NOT NULL DEFAULT 0 COMMENT '创建时的用户会话版本快照',
    `revoked_at` DATETIME(3) DEFAULT NULL COMMENT '会话撤销时间（UTC）；NULL为未撤销',
    `revoke_reason` VARCHAR(48) DEFAULT NULL COMMENT '撤销原因：LOGOUT/REUSE/ADMIN/PASSWORD_CHANGE等',
    `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID；0表示系统',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人ID；0表示系统',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间（UTC）',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常；已删除为本行ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_refresh_hash` (`refresh_token_hash`),
    KEY `idx_session_user_expires` (`user_id`, `refresh_expires_at`, `deleted`),
    KEY `idx_session_user_revoked` (`user_id`, `revoked_at`, `deleted`),
    KEY `idx_session_user_device` (`user_id`, `device_id`, `deleted`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户登录会话与Refresh Token表';

CREATE TABLE `auth_login_log` (
    `id` BIGINT NOT NULL COMMENT '全局唯一雪花ID',
    `user_id` BIGINT DEFAULT NULL COMMENT '关联用户ID；未知账号登录失败时允许为空',
    `session_id` BIGINT DEFAULT NULL COMMENT '成功登录关联的会话ID',
    `login_type` VARCHAR(24) NOT NULL COMMENT '登录方式：PASSWORD/OAUTH等',
    `identifier_masked` VARCHAR(191) DEFAULT NULL COMMENT '脱敏后的尝试登录标识',
    `success` TINYINT(1) NOT NULL COMMENT '是否成功：0失败，1成功',
    `failure_reason` VARCHAR(64) DEFAULT NULL COMMENT '失败原因编码；不包含敏感原始信息',
    `ip_address` VARCHAR(45) DEFAULT NULL COMMENT '请求IP地址',
    `user_agent` VARCHAR(512) DEFAULT NULL COMMENT '请求User-Agent',
    `client_type` VARCHAR(24) NOT NULL DEFAULT 'WEB' COMMENT '登录客户端类型',
    `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '登录事件实际发生时间（UTC）',
    `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID；0表示系统',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人ID；0表示系统',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间（UTC）',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常；已删除为本行ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_login_log_user_time` (`user_id`, `occurred_at`),
    KEY `idx_login_log_time_success` (`occurred_at`, `success`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='登录审计日志：仅记录脱敏标识，不存凭据';

CREATE TABLE `auth_security_log` (
    `id` BIGINT NOT NULL COMMENT '全局唯一雪花ID',
    `user_id` BIGINT NOT NULL COMMENT '被操作或受影响的用户ID',
    `event_type` VARCHAR(64) NOT NULL COMMENT '安全事件类型：CHANGE_PASSWORD/BIND_EMAIL/REVOKE_SESSION等',
    `event_result` TINYINT NOT NULL DEFAULT 1 COMMENT '事件执行结果：0失败，1成功',
    `session_id` BIGINT DEFAULT NULL COMMENT '相关会话ID',
    `ip_address` VARCHAR(45) DEFAULT NULL COMMENT '操作来源IP地址',
    `user_agent` VARCHAR(512) DEFAULT NULL COMMENT '操作来源User-Agent',
    `detail_json` JSON DEFAULT NULL COMMENT '脱敏后的安全事件扩展详情；禁止记录密钥和Token',
    `occurred_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '安全事件发生时间（UTC）',
    `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID；0表示系统',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人ID；0表示系统',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间（UTC）',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常；已删除为本行ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_security_log_user_time` (`user_id`, `occurred_at`),
    KEY `idx_security_log_event_time` (`event_type`, `occurred_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='账号安全事件审计日志';

CREATE TABLE `auth_mfa_factor` (
    `id` BIGINT NOT NULL COMMENT '全局唯一雪花ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID，关联sys_user.id',
    `factor_type` VARCHAR(24) NOT NULL COMMENT '认证器类型：TOTP等',
    `display_name` VARCHAR(80) NOT NULL DEFAULT '' COMMENT '认证器备注名称',
    `secret_ciphertext` VARBINARY(1024) DEFAULT NULL COMMENT '加密存储的认证器密钥密文；非明文',
    `key_id` VARCHAR(64) DEFAULT NULL COMMENT '密钥加密使用的KMS密钥标识',
    `verified_at` DATETIME(3) DEFAULT NULL COMMENT '验证激活时间（UTC）',
    `last_used_at` DATETIME(3) DEFAULT NULL COMMENT '最近使用时间（UTC）',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0待激活，1启用，2停用',
    `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID；0表示系统',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人ID；0表示系统',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间（UTC）',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常；已删除为本行ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    KEY `idx_mfa_factor_user_status` (`user_id`, `status`, `deleted`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户多因素认证器表';

CREATE TABLE `auth_recovery_code` (
    `id` BIGINT NOT NULL COMMENT '全局唯一雪花ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID，关联sys_user.id',
    `factor_id` BIGINT DEFAULT NULL COMMENT '相关认证器ID；NULL表示账户级恢复码',
    `code_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '随机恢复码的HMAC-SHA256摘要，不存明文',
    `used_at` DATETIME(3) DEFAULT NULL COMMENT '恢复码使用时间（UTC）；NULL为未使用',
    `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID；0表示系统',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人ID；0表示系统',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间（UTC）',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常；已删除为本行ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_recovery_code_hash` (`code_hash`),
    KEY `idx_recovery_code_user_unused` (`user_id`, `used_at`, `deleted`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='多因素认证一次性恢复码表';

CREATE TABLE `auth_role` (
    `id` BIGINT NOT NULL COMMENT '全局唯一雪花ID',
    `role_code` VARCHAR(64) NOT NULL COMMENT '唯一角色编码，如SUPER_ADMIN、USER',
    `role_name` VARCHAR(80) NOT NULL COMMENT '角色显示名称',
    `description` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '角色说明',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0否，1是',
    `is_system` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否系统内置角色，不允许随意删除',
    `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID；0表示系统',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人ID；0表示系统',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间（UTC）',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常；已删除为本行ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code_deleted` (`role_code`, `deleted`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='系统级角色定义表；不用于空间资源ACL';

CREATE TABLE `auth_permission` (
    `id` BIGINT NOT NULL COMMENT '全局唯一雪花ID',
    `permission_code` VARCHAR(128) NOT NULL COMMENT '权限编码，如system:user:read',
    `permission_name` VARCHAR(80) NOT NULL COMMENT '权限显示名称',
    `module_code` VARCHAR(48) NOT NULL COMMENT '所属功能模块编码',
    `description` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '权限用途说明',
    `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID；0表示系统',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人ID；0表示系统',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间（UTC）',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常；已删除为本行ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permission_code_deleted` (`permission_code`, `deleted`),
    KEY `idx_permission_module_deleted` (`module_code`, `deleted`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='系统级权限定义表；空间资源权限另行建模';

CREATE TABLE `auth_user_role` (
    `id` BIGINT NOT NULL COMMENT '全局唯一雪花ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID，关联sys_user.id',
    `role_id` BIGINT NOT NULL COMMENT '角色ID，关联auth_role.id',
    `expires_at` DATETIME(3) DEFAULT NULL COMMENT '角色授予过期时间；NULL为永久',
    `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID；0表示系统',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人ID；0表示系统',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间（UTC）',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常；已删除为本行ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role_deleted` (`user_id`, `role_id`, `deleted`),
    KEY `idx_user_role_role_deleted` (`role_id`, `deleted`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户与系统角色关联表';

CREATE TABLE `auth_role_permission` (
    `id` BIGINT NOT NULL COMMENT '全局唯一雪花ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID，关联auth_role.id',
    `permission_id` BIGINT NOT NULL COMMENT '权限ID，关联auth_permission.id',
    `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID；0表示系统',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（UTC）',
    `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人ID；0表示系统',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间（UTC）',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常；已删除为本行ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission_deleted` (`role_id`, `permission_id`, `deleted`),
    KEY `idx_role_permission_permission_deleted` (`permission_id`, `deleted`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='系统角色与系统权限关联表';

-- 内置系统角色；不预置任何管理员账号、初始密码或者用户身份。
INSERT INTO `auth_role` (`id`, `role_code`, `role_name`, `description`, `enabled`, `is_system`)
VALUES
    (1, 'SUPER_ADMIN', '超级管理员', '系统初始化时分配，拥有系统管理权限；资源ACL仍需独立校验', 1, 1),
    (2, 'USER', '普通用户', '默认注册用户的系统角色', 1, 1);
