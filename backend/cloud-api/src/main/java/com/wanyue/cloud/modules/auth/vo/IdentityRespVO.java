package com.wanyue.cloud.modules.auth.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDateTime;

/** 用户登录标识表：用户名、邮箱、手机号响应对象：默认屏蔽常见敏感凭据字段。 */
@Data
public class IdentityRespVO {
    /** 业务主键：前端 JSON 应序列化为字符串以规避 JS 精度问题。 */
    private Long id;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
    /** 所属用户ID，关联sys_user.id */
    private Long userId;
    /** 标识类型：USERNAME/EMAIL/PHONE */
    private String identityType;
    /** 规范化后的登录标识；用户名和邮箱不区分大小写，手机为E.164 */
    private String identifier;
    /** 是否为该类型的首选标识 */
    private Boolean isPrimary;
    /** 身份验证完成时间（UTC）；NULL为未验证 */
    private LocalDateTime verifiedAt;
    /** 最近作为登录标识使用的时间（UTC） */
    private LocalDateTime lastUsedAt;
}

