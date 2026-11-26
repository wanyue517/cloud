package com.wanyue.cloud.modules.auth.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDateTime;

/** 系统级角色定义表；不用于空间资源ACL响应对象：默认屏蔽常见敏感凭据字段。 */
@Data
public class RoleRespVO {
    /** 业务主键：前端 JSON 应序列化为字符串以规避 JS 精度问题。 */
    private Long id;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
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

