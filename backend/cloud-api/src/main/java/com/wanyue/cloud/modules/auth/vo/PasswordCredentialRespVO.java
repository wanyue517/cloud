package com.wanyue.cloud.modules.auth.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDateTime;

/** 用户密码凭证表：每用户最多一份有效密码响应对象：默认屏蔽常见敏感凭据字段。 */
@Data
public class PasswordCredentialRespVO {
    /** 业务主键：前端 JSON 应序列化为字符串以规避 JS 精度问题。 */
    private Long id;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
    /** 所属用户ID，关联sys_user.id */
    private Long userId;
    /** 最近一次修改密码的时间（UTC） */
    private LocalDateTime passwordChangedAt;
    /** 下次登录是否强制重置密码 */
    private Boolean resetRequired;
}

