-- 空间模块基础模型，依据 T002/T004/T005；MySQL 8.0.16+ / MyBatis-Plus。
-- DATETIME(3) 统一 UTC；应用连接必须使用 UTC 会话。
-- 逻辑关联不建物理外键，与现有用户/认证表保持一致；关联有效性由业务事务保障。
CREATE TABLE `space` (
    `id` BIGINT NOT NULL COMMENT '全局唯一雪花ID',
    `owner_user_id` BIGINT NOT NULL COMMENT '权威所有者，关联sys_user.id；共享容量计入此用户',
    `kind` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PERSONAL' COMMENT 'PERSONAL个人/SHARED共享/PRIVATE私密',
    `name` VARCHAR(50) NOT NULL COMMENT '空间显示名；同一所有者允许重名',
    `icon` VARCHAR(64) DEFAULT NULL COMMENT '前端图标编码，不存任意HTML或脚本',
    `color` CHAR(7) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '颜色值，格式为#RRGGBB',
    `description` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '空间用途描述',
    `status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DELETING/DELETED；必须使用生命周期业务方法变更',
    `viewer_download_allowed` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '共享查看者是否允许下载原件',
    `viewer_version_read_allowed` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '共享查看者是否允许读取历史版本',
    `access_version` BIGINT NOT NULL DEFAULT 0 COMMENT '权限变更版本，用于授权缓存及私密会话失效',
    `creation_request_key` VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '服务端生成的全局幂等键；注册使用REGISTER:userId，删除后不复用',
    `delete_requested_at` DATETIME(3) DEFAULT NULL COMMENT '最近一次删除流程发起时间UTC',
    `deleted_at` DATETIME(3) DEFAULT NULL COMMENT '完成逻辑删除的时间UTC',
    `purge_after` DATETIME(3) DEFAULT NULL COMMENT '允许进入永久清理的最早时间UTC，不代表可直接删除Blob',
    `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID；0表示系统',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
    `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人ID；0表示系统',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间UTC',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0有效；已删除为本行ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus乐观锁版本',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_space_creation_request` (`creation_request_key`),
    KEY `idx_space_owner_status` (`owner_user_id`, `deleted`, `status`, `id`),
    KEY `idx_space_purge` (`status`, `purge_after`, `id`),
    CONSTRAINT `ck_space_kind` CHECK (`kind` IN ('PERSONAL', 'SHARED', 'PRIVATE')),
    CONSTRAINT `ck_space_name` CHECK (CHAR_LENGTH(TRIM(`name`)) BETWEEN 1 AND 50),
    CONSTRAINT `ck_space_identity` CHECK (`id` > 0 AND `owner_user_id` > 0),
    CONSTRAINT `ck_space_flags` CHECK (`viewer_download_allowed` IN (0, 1) AND `viewer_version_read_allowed` IN (0, 1)),
    CONSTRAINT `ck_space_versions` CHECK (`version` >= 0 AND `access_version` >= 0),
    CONSTRAINT `ck_space_lifecycle` CHECK (
        (`status` = 'ACTIVE' AND `deleted` = 0 AND `delete_requested_at` IS NULL AND `deleted_at` IS NULL AND `purge_after` IS NULL)
        OR (`status` = 'DELETING' AND `deleted` = 0 AND `delete_requested_at` IS NOT NULL AND `deleted_at` IS NULL AND `purge_after` IS NULL)
        OR (`status` = 'DELETED' AND `deleted` = `id` AND `delete_requested_at` IS NOT NULL AND `deleted_at` IS NOT NULL
            AND `purge_after` IS NOT NULL AND `deleted_at` >= `delete_requested_at` AND `purge_after` >= `deleted_at`)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='空间资源容器；不创建伪根目录节点';

CREATE TABLE `space_member` (
    `id` BIGINT NOT NULL COMMENT '全局唯一雪花ID',
    `space_id` BIGINT NOT NULL COMMENT '关联space.id；只允许SHARED空间使用',
    `user_id` BIGINT NOT NULL COMMENT '成员用户，关联sys_user.id',
    `role` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'OWNER/MANAGER/EDITOR/VIEWER，不引用平台auth_role',
    `joined_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '成员接受邀请或所有者创建成员关系的时间UTC',
    `expires_at` DATETIME(3) DEFAULT NULL COMMENT '访问到期时间UTC；NULL永久；OWNER不能到期',
    `owner_space_id` BIGINT GENERATED ALWAYS AS (
        CASE WHEN `deleted` = 0 AND `role` = 'OWNER' THEN `space_id` ELSE NULL END
    ) VIRTUAL COMMENT '有效OWNER唯一性辅助列，不映射到DO',
    `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID；0表示系统',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
    `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人ID；0表示系统',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间UTC',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0有效；已退出或移除为本行ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus乐观锁版本',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_space_member_user_deleted` (`space_id`, `user_id`, `deleted`),
    UNIQUE KEY `uk_space_member_owner` (`owner_space_id`),
    KEY `idx_space_member_user` (`user_id`, `deleted`, `space_id`),
    CONSTRAINT `ck_space_member_role` CHECK (`role` IN ('OWNER', 'MANAGER', 'EDITOR', 'VIEWER')),
    CONSTRAINT `ck_space_member_identity` CHECK (`id` > 0 AND `space_id` > 0 AND `user_id` > 0),
    CONSTRAINT `ck_space_member_owner_expiry` CHECK (`role` <> 'OWNER' OR `expires_at` IS NULL),
    CONSTRAINT `ck_space_member_expiry` CHECK (`expires_at` IS NULL OR `expires_at` > `joined_at`),
    CONSTRAINT `ck_space_member_base` CHECK (`deleted` IN (0, `id`) AND `version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='共享空间成员关系，独立于文件夹授权';

CREATE TABLE `space_user_preference` (
    `id` BIGINT NOT NULL COMMENT '全局唯一雪花ID',
    `user_id` BIGINT NOT NULL COMMENT '偏好所属用户，关联sys_user.id',
    `space_id` BIGINT NOT NULL COMMENT '关联space.id；记录存在不代表有访问权限',
    `sort_order` BIGINT NOT NULL DEFAULT 0 COMMENT '该用户的空间导航顺序；按sort_order、space_id稳定排序',
    `tree_collapsed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '该用户是否折叠空间目录树',
    `created_by` BIGINT NOT NULL DEFAULT 0 COMMENT '创建人ID；0表示系统',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间UTC',
    `updated_by` BIGINT NOT NULL DEFAULT 0 COMMENT '最后修改人ID；0表示系统',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最后修改时间UTC',
    `deleted` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0有效；已删除为本行ID',
    `version` INT NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus乐观锁版本',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_space_preference_user_deleted` (`user_id`, `space_id`, `deleted`),
    KEY `idx_space_preference_sort` (`user_id`, `deleted`, `sort_order`, `space_id`),
    CONSTRAINT `ck_space_preference_identity` CHECK (`id` > 0 AND `user_id` > 0 AND `space_id` > 0),
    CONSTRAINT `ck_space_preference_base` CHECK (`tree_collapsed` IN (0, 1) AND `deleted` IN (0, `id`) AND `version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户空间导航偏好；默认入口仍存sys_user_setting';
