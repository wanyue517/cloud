package com.wanyue.cloud.modules.auth.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 用户登录安全状态表新增请求：敏感认证字段不通过通用 CRUD 处理。 */
@Data
public class UserSecurityCreateReqVO {
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

