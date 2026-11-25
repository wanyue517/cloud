package com.wanyue.cloud.modules.auth.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

/** 用户登录安全状态表更新请求：需携带读取时获取的 version。 */
@Data
public class UserSecurityUpdateReqVO {

    @NotNull(message = "版本号不能为空")
    private Integer version;
    /** 所属用户ID，关联sys_user.id */
    private Long userId;
    /** 连续密码登录失败次数 */
    private Integer failedLoginCount;
    /** 临时登录锁定截止时间（UTC） */
    private LocalDateTime lockedUntil;
    /** 是否允许密码登录：0否，1是 */
    private Boolean passwordLoginEnabled;
    /** 会话版本；递增可使旧令牌失效 */
    private Integer sessionVersion;
    /** 是否强制使用多因素认证 */
    private Boolean mfaRequired;
    /** 最后一次成功密码登录时间（UTC） */
    private LocalDateTime lastPasswordLoginAt;
}

