package com.wanyue.cloud.modules.auth.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 系统级权限定义表；空间资源权限另行建模新增请求：敏感认证字段不通过通用 CRUD 处理。 */
@Data
public class PermissionCreateReqVO {
    /** 权限编码，如system:user:read */
    private String permissionCode;
    /** 权限显示名称 */
    private String permissionName;
    /** 所属功能模块编码 */
    private String moduleCode;
    /** 权限用途说明 */
    private String description;
}

