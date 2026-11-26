package com.wanyue.cloud.modules.auth.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDateTime;

/** 系统角色与系统权限关联表响应对象：默认屏蔽常见敏感凭据字段。 */
@Data
public class RolePermissionRespVO {
    /** 业务主键：前端 JSON 应序列化为字符串以规避 JS 精度问题。 */
    private Long id;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
    /** 角色ID，关联auth_role.id */
    private Long roleId;
    /** 权限ID，关联auth_permission.id */
    private Long permissionId;
}

