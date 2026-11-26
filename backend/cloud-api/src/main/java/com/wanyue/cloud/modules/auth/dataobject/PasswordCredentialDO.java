package com.wanyue.cloud.modules.auth.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wanyue.cloud.common.domain.BaseDO;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 用户密码凭证表：每用户最多一份有效密码（auth_password_credential）持久化对象。
 * 公共字段由 BaseDO 定义，不重复生成。
 */
@Getter
@Setter
@TableName("auth_password_credential")
public class PasswordCredentialDO extends BaseDO {
    /** 所属用户ID，关联sys_user.id */
    @TableField("user_id")
    private Long userId;
    /** PasswordEncoder生成的密码哈希；禁止存储明文 */
    @TableField("password_hash")
    private String passwordHash;
    /** 最近一次修改密码的时间（UTC） */
    @TableField("password_changed_at")
    private LocalDateTime passwordChangedAt;
    /** 下次登录是否强制重置密码 */
    @TableField("reset_required")
    private Boolean resetRequired;
}

