package com.wanyue.cloud.modules.auth.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDateTime;

/** 用户与系统角色关联表响应对象：默认屏蔽常见敏感凭据字段。 */
@Data
public class UserRoleRespVO {
    /** 业务主键：前端 JSON 应序列化为字符串以规避 JS 精度问题。 */
    private Long id;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
    /** 用户ID，关联sys_user.id */
    private Long userId;
    /** 角色ID，关联auth_role.id */
    private Long roleId;
    /** 角色授予过期时间；NULL为永久 */
    private LocalDateTime expiresAt;
}

