package com.wanyue.cloud.modules.auth.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wanyue.cloud.common.domain.BaseDO;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 系统级权限定义表；空间资源权限另行建模（auth_permission）持久化对象。
 * 公共字段由 BaseDO 定义，不重复生成。
 */
@Getter
@Setter
@TableName("auth_permission")
public class PermissionDO extends BaseDO {
    /** 权限编码，如system:user:read */
    @TableField("permission_code")
    private String permissionCode;
    /** 权限显示名称 */
    @TableField("permission_name")
    private String permissionName;
    /** 所属功能模块编码 */
    @TableField("module_code")
    private String moduleCode;
    /** 权限用途说明 */
    @TableField("description")
    private String description;
}

