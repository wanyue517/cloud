package com.wanyue.cloud.modules.auth.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 系统角色与系统权限关联表新增请求：敏感认证字段不通过通用 CRUD 处理。 */
@Data
public class RolePermissionCreateReqVO {
    /** 角色ID，关联auth_role.id */
    private Long roleId;
    /** 权限ID，关联auth_permission.id */
    private Long permissionId;
}

