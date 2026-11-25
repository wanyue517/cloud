package com.wanyue.cloud.modules.auth.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wanyue.cloud.common.domain.BaseDO;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 系统角色与系统权限关联表（auth_role_permission）持久化对象。
 * 公共字段由 BaseDO 定义，不重复生成。
 */
@Getter
@Setter
@TableName("auth_role_permission")
public class RolePermissionDO extends BaseDO {
    /** 角色ID，关联auth_role.id */
    @TableField("role_id")
    private Long roleId;
    /** 权限ID，关联auth_permission.id */
    @TableField("permission_id")
    private Long permissionId;
}

