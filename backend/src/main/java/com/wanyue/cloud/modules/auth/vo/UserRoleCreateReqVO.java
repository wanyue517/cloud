package com.wanyue.cloud.modules.auth.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 用户与系统角色关联表新增请求：敏感认证字段不通过通用 CRUD 处理。 */
@Data
public class UserRoleCreateReqVO {
    /** 用户ID，关联sys_user.id */
    private Long userId;
    /** 角色ID，关联auth_role.id */
    private Long roleId;
    /** 角色授予过期时间；NULL为永久 */
    private LocalDateTime expiresAt;
}

