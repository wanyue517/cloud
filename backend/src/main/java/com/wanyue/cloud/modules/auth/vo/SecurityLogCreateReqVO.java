package com.wanyue.cloud.modules.auth.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 账号安全事件审计日志新增请求：敏感认证字段不通过通用 CRUD 处理。 */
@Data
public class SecurityLogCreateReqVO {
    /** 被操作或受影响的用户ID */
    private Long userId;
    /** 安全事件类型：CHANGE_PASSWORD/BIND_EMAIL/REVOKE_SESSION等 */
    private String eventType;
    /** 事件执行结果：0失败，1成功 */
    private Integer eventResult;
    /** 相关会话ID */
    private Long sessionId;
    /** 操作来源IP地址 */
    private String ipAddress;
    /** 操作来源User-Agent */
    private String userAgent;
    /** 脱敏后的安全事件扩展详情；禁止记录密钥和Token */
    private String detailJson;
    /** 安全事件发生时间（UTC） */
    private LocalDateTime occurredAt;
}

