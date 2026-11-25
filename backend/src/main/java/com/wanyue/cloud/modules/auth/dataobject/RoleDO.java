package com.wanyue.cloud.modules.auth.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wanyue.cloud.common.domain.BaseDO;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 系统级角色定义表；不用于空间资源ACL（auth_role）持久化对象。
 * 公共字段由 BaseDO 定义，不重复生成。
 */
@Getter
@Setter
@TableName("auth_role")
public class RoleDO extends BaseDO {
    /** 唯一角色编码，如SUPER_ADMIN、USER */
    @TableField("role_code")
    private String roleCode;
    /** 角色显示名称 */
    @TableField("role_name")
    private String roleName;
    /** 角色说明 */
    @TableField("description")
    private String description;
    /** 是否启用：0否，1是 */
    @TableField("enabled")
    private Boolean enabled;
    /** 是否系统内置角色，不允许随意删除 */
    @TableField("is_system")
    private Boolean isSystem;
}

