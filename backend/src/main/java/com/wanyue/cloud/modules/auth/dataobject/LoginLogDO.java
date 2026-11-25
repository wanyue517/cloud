package com.wanyue.cloud.modules.auth.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wanyue.cloud.common.domain.BaseDO;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 登录审计日志：仅记录脱敏标识，不存凭据（auth_login_log）持久化对象。
 * 公共字段由 BaseDO 定义，不重复生成。
 */
@Getter
@Setter
@TableName("auth_login_log")
public class LoginLogDO extends BaseDO {
    /** 关联用户ID；未知账号登录失败时允许为空 */
    @TableField("user_id")
    private Long userId;
    /** 成功登录关联的会话ID */
    @TableField("session_id")
    private Long sessionId;
    /** 登录方式：PASSWORD/OAUTH等 */
    @TableField("login_type")
    private String loginType;
    /** 脱敏后的尝试登录标识 */
    @TableField("identifier_masked")
    private String identifierMasked;
    /** 是否成功：0失败，1成功 */
    @TableField("success")
    private Boolean success;
    /** 失败原因编码；不包含敏感原始信息 */
    @TableField("failure_reason")
    private String failureReason;
    /** 请求IP地址 */
    @TableField("ip_address")
    private String ipAddress;
    /** 请求User-Agent */
    @TableField("user_agent")
    private String userAgent;
    /** 登录客户端类型 */
    @TableField("client_type")
    private String clientType;
    /** 登录事件实际发生时间（UTC） */
    @TableField("occurred_at")
    private LocalDateTime occurredAt;
}

