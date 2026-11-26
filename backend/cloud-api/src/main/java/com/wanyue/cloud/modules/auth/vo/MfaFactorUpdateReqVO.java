package com.wanyue.cloud.modules.auth.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户多因素认证器表更新请求：需携带读取时获取的 version。 */
@Data
public class MfaFactorUpdateReqVO {

    @NotNull(message = "版本号不能为空")
    private Integer version;
    /** 所属用户ID，关联sys_user.id */
    private Long userId;
    /** 认证器类型：TOTP等 */
    private String factorType;
    /** 认证器备注名称 */
    private String displayName;
    /** 密钥加密使用的KMS密钥标识 */
    private String keyId;
    /** 验证激活时间（UTC） */
    private LocalDateTime verifiedAt;
    /** 最近使用时间（UTC） */
    private LocalDateTime lastUsedAt;
    /** 状态：0待激活，1启用，2停用 */
    private Integer status;
}

