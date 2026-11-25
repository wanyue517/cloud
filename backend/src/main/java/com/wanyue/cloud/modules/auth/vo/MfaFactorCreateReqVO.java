package com.wanyue.cloud.modules.auth.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 用户多因素认证器表新增请求：敏感认证字段不通过通用 CRUD 处理。 */
@Data
public class MfaFactorCreateReqVO {
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

