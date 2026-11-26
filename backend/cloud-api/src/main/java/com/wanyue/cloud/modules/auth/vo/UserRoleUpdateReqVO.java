package com.wanyue.cloud.modules.auth.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

/** 用户与系统角色关联表更新请求：需携带读取时获取的 version。 */
@Data
public class UserRoleUpdateReqVO {

    @NotNull(message = "版本号不能为空")
    private Integer version;
    /** 用户ID，关联sys_user.id */
    private Long userId;
    /** 角色ID，关联auth_role.id */
    private Long roleId;
    /** 角色授予过期时间；NULL为永久 */
    private LocalDateTime expiresAt;
}

