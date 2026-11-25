package com.wanyue.cloud.modules.auth.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wanyue.cloud.common.domain.BaseDO;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 账号安全事件审计日志（auth_security_log）持久化对象。
 * 公共字段由 BaseDO 定义，不重复生成。
 */
@Getter
@Setter
@TableName("auth_security_log")
public class SecurityLogDO extends BaseDO {
    /** 被操作或受影响的用户ID */
    @TableField("user_id")
    private Long userId;
    /** 安全事件类型：CHANGE_PASSWORD/BIND_EMAIL/REVOKE_SESSION等 */
    @TableField("event_type")
    private String eventType;
    /** 事件执行结果：0失败，1成功 */
    @TableField("event_result")
    private Integer eventResult;
    /** 相关会话ID */
    @TableField("session_id")
    private Long sessionId;
    /** 操作来源IP地址 */
    @TableField("ip_address")
    private String ipAddress;
    /** 操作来源User-Agent */
    @TableField("user_agent")
    private String userAgent;
    /** 脱敏后的安全事件扩展详情；禁止记录密钥和Token */
    @TableField("detail_json")
    private String detailJson;
    /** 安全事件发生时间（UTC） */
    @TableField("occurred_at")
    private LocalDateTime occurredAt;
}

