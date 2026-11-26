package com.wanyue.cloud.modules.auth.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

/** 用户密码凭证表：每用户最多一份有效密码更新请求：需携带读取时获取的 version。 */
@Data
public class PasswordCredentialUpdateReqVO {

    @NotNull(message = "版本号不能为空")
    private Integer version;
    /** 所属用户ID，关联sys_user.id */
    private Long userId;
    /** 最近一次修改密码的时间（UTC） */
    private LocalDateTime passwordChangedAt;
    /** 下次登录是否强制重置密码 */
    private Boolean resetRequired;
}

