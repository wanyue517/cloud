package com.wanyue.cloud.modules.auth.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDateTime;

/** 系统级权限定义表；空间资源权限另行建模响应对象：默认屏蔽常见敏感凭据字段。 */
@Data
public class PermissionRespVO {
    /** 业务主键：前端 JSON 应序列化为字符串以规避 JS 精度问题。 */
    private Long id;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
    /** 权限编码，如system:user:read */
    private String permissionCode;
    /** 权限显示名称 */
    private String permissionName;
    /** 所属功能模块编码 */
    private String moduleCode;
    /** 权限用途说明 */
    private String description;
}

