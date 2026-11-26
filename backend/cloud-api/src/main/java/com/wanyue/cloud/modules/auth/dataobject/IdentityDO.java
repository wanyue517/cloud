package com.wanyue.cloud.modules.auth.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wanyue.cloud.common.domain.BaseDO;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 用户登录标识表：用户名、邮箱、手机号（auth_identity）持久化对象。
 * 公共字段由 BaseDO 定义，不重复生成。
 */
@Getter
@Setter
@TableName("auth_identity")
public class IdentityDO extends BaseDO {
    /** 所属用户ID，关联sys_user.id */
    @TableField("user_id")
    private Long userId;
    /** 标识类型：USERNAME/EMAIL/PHONE */
    @TableField("identity_type")
    private String identityType;
    /** 规范化后的登录标识；用户名和邮箱不区分大小写，手机为E.164 */
    @TableField("identifier")
    private String identifier;
    /** 是否为该类型的首选标识 */
    @TableField("is_primary")
    private Boolean isPrimary;
    /** 身份验证完成时间（UTC）；NULL为未验证 */
    @TableField("verified_at")
    private LocalDateTime verifiedAt;
    /** 最近作为登录标识使用的时间（UTC） */
    @TableField("last_used_at")
    private LocalDateTime lastUsedAt;
}

