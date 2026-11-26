package com.wanyue.cloud.modules.auth.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

/** 系统角色与系统权限关联表更新请求：需携带读取时获取的 version。 */
@Data
public class RolePermissionUpdateReqVO {

    @NotNull(message = "版本号不能为空")
    private Integer version;
    /** 角色ID，关联auth_role.id */
    private Long roleId;
    /** 权限ID，关联auth_permission.id */
    private Long permissionId;
}

