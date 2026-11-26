package com.wanyue.cloud.modules.auth.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wanyue.cloud.common.domain.BaseDO;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 用户登录安全状态表（auth_user_security）持久化对象。
 * 公共字段由 BaseDO 定义，不重复生成。
 */
@Getter
@Setter
@TableName("auth_user_security")
public class UserSecurityDO extends BaseDO {
    /** 所属用户ID，关联sys_user.id */
    @TableField("user_id")
    private Long userId;
    /** 连续密码登录失败次数 */
    @TableField("failed_login_count")
    private Integer failedLoginCount;
    /** 临时登录锁定截止时间（UTC） */
    @TableField("locked_until")
    private LocalDateTime lockedUntil;
    /** 是否允许密码登录：0否，1是 */
    @TableField("password_login_enabled")
    private Boolean passwordLoginEnabled;
    /** 会话版本；递增可使旧令牌失效 */
    @TableField("session_version")
    private Integer sessionVersion;
    /** 是否强制使用多因素认证 */
    @TableField("mfa_required")
    private Boolean mfaRequired;
    /** 最后一次成功密码登录时间（UTC） */
    @TableField("last_password_login_at")
    private LocalDateTime lastPasswordLoginAt;
}

