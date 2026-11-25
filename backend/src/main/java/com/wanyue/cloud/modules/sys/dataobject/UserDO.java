package com.wanyue.cloud.modules.sys.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wanyue.cloud.common.domain.BaseDO;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 系统用户主表（sys_user）持久化对象。
 * 公共字段由 BaseDO 定义，不重复生成。
 */
@Getter
@Setter
@TableName("sys_user")
public class UserDO extends BaseDO {
    /** 用户显示名称 */
    @TableField("display_name")
    private String displayName;
    /** 用户头像资源地址 */
    @TableField("avatar_url")
    private String avatarUrl;
    /** 用户状态：0禁用，1正常，2冻结 */
    @TableField("status")
    private Integer status;
    /** 注册来源：PASSWORD/OAUTH/INVITE等 */
    @TableField("register_source")
    private String registerSource;
    /** 账号注册时间（UTC） */
    @TableField("registered_at")
    private LocalDateTime registeredAt;
    /** 最近成功登录时间（UTC） */
    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;
}

