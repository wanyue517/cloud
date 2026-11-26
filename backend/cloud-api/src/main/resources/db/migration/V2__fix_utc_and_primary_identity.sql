-- V1 已执行，禁止修改。只修复与 UTC 审计时间相差恰好八小时的已确认历史默认值。
-- 显式保留 updated_at，避免修复动作触发 ON UPDATE 改写原审计时间。
UPDATE sys_user
SET registered_at = DATE_SUB(registered_at, INTERVAL 8 HOUR), updated_at = updated_at
WHERE ABS(TIMESTAMPDIFF(MICROSECOND, created_at, registered_at) - 28800000000) <= 1000000;

UPDATE auth_password_credential
SET password_changed_at = DATE_SUB(password_changed_at, INTERVAL 8 HOUR), updated_at = updated_at
WHERE ABS(TIMESTAMPDIFF(MICROSECOND, created_at, password_changed_at) - 28800000000) <= 1000000;

UPDATE auth_security_log
SET occurred_at = DATE_SUB(occurred_at, INTERVAL 8 HOUR), updated_at = updated_at
WHERE ABS(TIMESTAMPDIFF(MICROSECOND, created_at, occurred_at) - 28800000000) <= 1000000;

UPDATE auth_login_log
SET occurred_at = DATE_SUB(occurred_at, INTERVAL 8 HOUR), updated_at = updated_at
WHERE ABS(TIMESTAMPDIFF(MICROSECOND, created_at, occurred_at) - 28800000000) <= 1000000;

-- 每组保留既有首选身份；没有首选时选择最早创建的有效身份，ID用于稳定排序。
CREATE TEMPORARY TABLE migration_primary_identity AS
SELECT id,
       ROW_NUMBER() OVER (
           PARTITION BY user_id, identity_type
           ORDER BY is_primary DESC, created_at ASC, id ASC
       ) AS primary_rank
FROM auth_identity
WHERE deleted = 0;

UPDATE auth_identity i
JOIN migration_primary_identity p ON p.id = i.id
SET i.is_primary = (p.primary_rank = 1), i.version = i.version + 1, i.updated_at = i.updated_at
WHERE i.is_primary <> (p.primary_rank = 1);

DROP TEMPORARY TABLE migration_primary_identity;

-- 非首选或已删除行生成 NULL，允许多行；有效首选行按用户和类型唯一。
ALTER TABLE auth_identity
    ADD COLUMN primary_user_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 AND is_primary = 1 THEN user_id ELSE NULL END
    ) VIRTUAL,
    ADD UNIQUE KEY uk_identity_primary_user_type (primary_user_id, identity_type);
