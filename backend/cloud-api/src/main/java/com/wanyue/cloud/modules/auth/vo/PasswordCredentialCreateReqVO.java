package com.wanyue.cloud.modules.auth.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 用户密码凭证表：每用户最多一份有效密码新增请求：敏感认证字段不通过通用 CRUD 处理。 */
@Data
public class PasswordCredentialCreateReqVO {
    /** 所属用户ID，关联sys_user.id */
    private Long userId;
    /** 最近一次修改密码的时间（UTC） */
    private LocalDateTime passwordChangedAt;
    /** 下次登录是否强制重置密码 */
    private Boolean resetRequired;
}

