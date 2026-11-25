package com.wanyue.cloud.modules.auth.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

/** 系统级权限定义表；空间资源权限另行建模更新请求：需携带读取时获取的 version。 */
@Data
public class PermissionUpdateReqVO {

    @NotNull(message = "版本号不能为空")
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

