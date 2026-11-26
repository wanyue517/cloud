package com.wanyue.cloud.modules.auth.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDateTime;

/** 用户登录安全状态表响应对象：默认屏蔽常见敏感凭据字段。 */
@Data
public class UserSecurityRespVO {
    /** 业务主键：前端 JSON 应序列化为字符串以规避 JS 精度问题。 */
    private Long id;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
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

