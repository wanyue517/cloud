package com.wanyue.cloud.modules.auth.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wanyue.cloud.common.domain.BaseDO;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 用户与系统角色关联表（auth_user_role）持久化对象。
 * 公共字段由 BaseDO 定义，不重复生成。
 */
@Getter
@Setter
@TableName("auth_user_role")
public class UserRoleDO extends BaseDO {
    /** 用户ID，关联sys_user.id */
    @TableField("user_id")
    private Long userId;
    /** 角色ID，关联auth_role.id */
    @TableField("role_id")
    private Long roleId;
    /** 角色授予过期时间；NULL为永久 */
    @TableField("expires_at")
    private LocalDateTime expiresAt;
}

