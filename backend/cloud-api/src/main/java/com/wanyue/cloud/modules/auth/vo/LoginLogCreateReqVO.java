package com.wanyue.cloud.modules.auth.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 登录审计日志：仅记录脱敏标识，不存凭据新增请求：敏感认证字段不通过通用 CRUD 处理。 */
@Data
public class LoginLogCreateReqVO {
    /** 关联用户ID；未知账号登录失败时允许为空 */
    private Long userId;
    /** 成功登录关联的会话ID */
    private Long sessionId;
    /** 登录方式：PASSWORD/OAUTH等 */
    private String loginType;
    /** 脱敏后的尝试登录标识 */
    private String identifierMasked;
    /** 是否成功：0失败，1成功 */
    private Boolean success;
    /** 失败原因编码；不包含敏感原始信息 */
    private String failureReason;
    /** 请求IP地址 */
    private String ipAddress;
    /** 请求User-Agent */
    private String userAgent;
    /** 登录客户端类型 */
    private String clientType;
    /** 登录事件实际发生时间（UTC） */
    private LocalDateTime occurredAt;
}

