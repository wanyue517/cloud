package com.wanyue.cloud.modules.auth.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

/** 系统级角色定义表；不用于空间资源ACL更新请求：需携带读取时获取的 version。 */
@Data
public class RoleUpdateReqVO {

    @NotNull(message = "版本号不能为空")
    private Integer version;
    /** 唯一角色编码，如SUPER_ADMIN、USER */
    private String roleCode;
    /** 角色显示名称 */
    private String roleName;
    /** 角色说明 */
    private String description;
    /** 是否启用：0否，1是 */
    private Boolean enabled;
    /** 是否系统内置角色，不允许随意删除 */
    private Boolean isSystem;
}

