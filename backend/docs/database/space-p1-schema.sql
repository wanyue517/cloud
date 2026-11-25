-- P1 扩展设计稿，故意放在 Flyway 扫描目录之外。
-- 实现 T097/T143 前审阅并拆为新的正式迁移；依赖 V3 基础空间表。
-- 不包含共享文件夹授权与公开分享链接，避免扩大空间模块职责。
CREATE TABLE `space_invitation` (
    `id` BIGINT NOT NULL COMMENT '全局唯一雪花ID',
    `space_id` BIGINT NOT NULL COMMENT '目标共享空间space.id，接受时重新校验类型和状态',
    `inviter_user_id` BIGINT NOT NULL COMMENT '发起邀请的用户，接受时重新校验邀请人权限',
    `invitee_email` VARCHAR(191) NOT NULL COMMENT '规范化的接收邮箱；接受人必须拥有该已验证邮箱身份',
    `role` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'MANAGER/EDITOR/VIEWER；转让不能用普通成员邀请完成',
    `token_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '高熵邀请令牌的HMAC-SHA256摘要；不存明文令牌',
    `status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/DECLINED/REVOKED/EXPIRED',
    `expires_at` DATETIME(3) NOT NULL COMMENT '邀请绝对到期时间UTC；查询时即使仍为PENDING也要核对',
    `processed_at` DATETIME(3) DEFAULT NULL COMMENT '进入终态的时间UTC',
    `accepted_by_user_id` BIGINT DEFAULT NULL COMMENT '实际接受用户ID，仅ACCEPTED时非空',
    `pending_email` VARCHAR(191) GENERATED ALWAYS AS (
        CASE WHEN `deleted` = 0 AND `status` = 'PENDING' THEN `invitee_email` ELSE NULL END
    ) VIRTUAL COMMENT '每空间每邮箱最多一份待处理邀请；过期行需先结算为EXPIRED',
    `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID；0表示系统',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
    `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人ID；0表示系统',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间UTC',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0有效；已归档删除为本行ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus乐观锁版本',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_space_invitation_token` (`token_hash`),
    UNIQUE KEY `uk_space_invitation_pending` (`space_id`, `pending_email`),
    KEY `idx_space_invitation_recipient` (`invitee_email`, `deleted`, `status`, `expires_at`),
    KEY `idx_space_invitation_expiry` (`status`, `expires_at`, `id`),
    CONSTRAINT `ck_space_invitation_role` CHECK (`role` IN ('MANAGER', 'EDITOR', 'VIEWER')),
    CONSTRAINT `ck_space_invitation_identity` CHECK (`id` > 0 AND `space_id` > 0 AND `inviter_user_id` > 0),
    CONSTRAINT `ck_space_invitation_expiry` CHECK (`expires_at` > `created_at`),
    CONSTRAINT `ck_space_invitation_status` CHECK (
        (`status` = 'PENDING' AND `processed_at` IS NULL AND `accepted_by_user_id` IS NULL)
        OR (`status` = 'ACCEPTED' AND `processed_at` IS NOT NULL AND `accepted_by_user_id` IS NOT NULL AND `accepted_by_user_id` > 0)
        OR (`status` IN ('DECLINED', 'REVOKED', 'EXPIRED') AND `processed_at` IS NOT NULL AND `accepted_by_user_id` IS NULL)
    ),
    CONSTRAINT `ck_space_invitation_base` CHECK (`deleted` IN (0, `id`) AND `version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='共享空间邀请；邀请本身不授予访问权';

CREATE TABLE `space_private_security` (
    `id` BIGINT NOT NULL COMMENT '全局唯一雪花ID',
    `space_id` BIGINT NOT NULL COMMENT '关联PRIVATE类型space.id；同事务创建并校验',
    `password_hash` VARCHAR(255) NOT NULL COMMENT '独立验证密码的自适应哈希，不复用账号密码哈希',
    `credential_version` BIGINT NOT NULL DEFAULT 1 COMMENT '验证凭证版本；修改密码时递增以作废旧解锁会话',
    `idle_timeout_seconds` INT NOT NULL DEFAULT 900 COMMENT '空闲锁定秒数，建议默认15分钟，可配置',
    `max_session_seconds` INT NOT NULL DEFAULT 3600 COMMENT '单次解锁绝对有效期秒数，建议默认1小时，可配置',
    `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID；0表示系统',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
    `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人ID；0表示系统',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间UTC',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0有效；已删除为本行ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus乐观锁版本',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_space_private_security_deleted` (`space_id`, `deleted`),
    CONSTRAINT `ck_space_private_identity` CHECK (`id` > 0 AND `space_id` > 0),
    CONSTRAINT `ck_space_private_timeout` CHECK (`idle_timeout_seconds` BETWEEN 1 AND 86400 AND `max_session_seconds` BETWEEN `idle_timeout_seconds` AND 86400),
    CONSTRAINT `ck_space_private_base` CHECK (`credential_version` > 0 AND `deleted` IN (0, `id`) AND `version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='私密空间独立验证配置，不表示内容加密';

CREATE TABLE `space_unlock_session` (
    `id` BIGINT NOT NULL COMMENT '全局唯一雪花ID',
    `space_id` BIGINT NOT NULL COMMENT '已解锁私密空间space.id',
    `user_id` BIGINT NOT NULL COMMENT '解锁用户；必须与空间当前所有者一致',
    `auth_session_id` BIGINT NOT NULL COMMENT '关联auth_session.id；账号会话失效时解锁也失效',
    `token_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '独立解锁令牌的HMAC-SHA256摘要，不存明文',
    `credential_version` BIGINT NOT NULL COMMENT '验证时的私密凭证版本快照',
    `space_access_version` BIGINT NOT NULL COMMENT '验证时的空间权限版本快照',
    `last_used_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近一次通过全部鉴权的访问时间UTC',
    `idle_expires_at` DATETIME(3) NOT NULL COMMENT '空闲到期时间UTC；续期不得超过绝对到期时间',
    `expires_at` DATETIME(3) NOT NULL COMMENT '绝对到期时间UTC',
    `revoked_at` DATETIME(3) DEFAULT NULL COMMENT '主动锁定或撤销时间UTC',
    `revoke_reason` VARCHAR(48) DEFAULT NULL COMMENT 'LOCK/LOGOUT/PASSWORD_CHANGE/SPACE_DELETE等',
    `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID；0表示系统',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
    `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人ID；0表示系统',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间UTC',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0有效；已删除为本行ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus乐观锁版本',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_space_unlock_token` (`token_hash`),
    KEY `idx_space_unlock_space_user` (`space_id`, `user_id`, `deleted`, `revoked_at`),
    KEY `idx_space_unlock_auth_session` (`auth_session_id`, `deleted`),
    KEY `idx_space_unlock_expiry` (`expires_at`, `id`),
    KEY `idx_space_unlock_idle` (`idle_expires_at`, `id`),
    CONSTRAINT `ck_space_unlock_identity` CHECK (`id` > 0 AND `space_id` > 0 AND `user_id` > 0 AND `auth_session_id` > 0),
    CONSTRAINT `ck_space_unlock_time` CHECK (`created_at` <= `last_used_at` AND `last_used_at` < `idle_expires_at` AND `idle_expires_at` <= `expires_at`),
    CONSTRAINT `ck_space_unlock_revocation` CHECK (
        (`revoked_at` IS NULL AND `revoke_reason` IS NULL)
        OR (`revoked_at` IS NOT NULL AND `revoke_reason` IS NOT NULL)
    ),
    CONSTRAINT `ck_space_unlock_base` CHECK (`credential_version` > 0 AND `space_access_version` >= 0 AND `deleted` IN (0, `id`) AND `version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='服务端私密空间短期解锁会话，独立于普通登录态';
