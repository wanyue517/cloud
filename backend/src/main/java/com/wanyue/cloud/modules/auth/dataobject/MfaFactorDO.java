package com.wanyue.cloud.modules.auth.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wanyue.cloud.common.domain.BaseDO;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户多因素认证器表（auth_mfa_factor）持久化对象。
 * 公共字段由 BaseDO 定义，不重复生成。
 */
@Getter
@Setter
@TableName("auth_mfa_factor")
public class MfaFactorDO extends BaseDO {
    /** 所属用户ID，关联sys_user.id */
    @TableField("user_id")
    private Long userId;
    /** 认证器类型：TOTP等 */
    @TableField("factor_type")
    private String factorType;
    /** 认证器备注名称 */
    @TableField("display_name")
    private String displayName;
    /** 加密存储的认证器密钥密文；非明文 */
    @TableField("secret_ciphertext")
    private byte[] secretCiphertext;
    /** 密钥加密使用的KMS密钥标识 */
    @TableField("key_id")
    private String keyId;
    /** 验证激活时间（UTC） */
    @TableField("verified_at")
    private LocalDateTime verifiedAt;
    /** 最近使用时间（UTC） */
    @TableField("last_used_at")
    private LocalDateTime lastUsedAt;
    /** 状态：0待激活，1启用，2停用 */
    @TableField("status")
    private Integer status;
}

