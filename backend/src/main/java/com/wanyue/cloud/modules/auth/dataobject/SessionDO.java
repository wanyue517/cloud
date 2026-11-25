package com.wanyue.cloud.modules.auth.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wanyue.cloud.common.domain.BaseDO;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 用户登录会话与Refresh Token表（auth_session）持久化对象。
 * 公共字段由 BaseDO 定义，不重复生成。
 */
@Getter
@Setter
@TableName("auth_session")
public class SessionDO extends BaseDO {
    /** 所属用户ID，关联sys_user.id */
    @TableField("user_id")
    private Long userId;
    /** Refresh Token的HMAC-SHA256十六进制摘要；禁止存明文 */
    @TableField("refresh_token_hash")
    private String refreshTokenHash;
    /** Refresh Token到期时间（UTC） */
    @TableField("refresh_expires_at")
    private LocalDateTime refreshExpiresAt;
    /** 客户端生成的设备标识；不作为安全认证凭据 */
    @TableField("device_id")
    private String deviceId;
    /** 设备显示名称 */
    @TableField("device_name")
    private String deviceName;
    /** 客户端类型：WEB/DESKTOP/ANDROID/IOS等 */
    @TableField("client_type")
    private String clientType;
    /** 最近会话客户端User-Agent */
    @TableField("user_agent")
    private String userAgent;
    /** 最近使用的IPv4或IPv6地址 */
    @TableField("ip_address")
    private String ipAddress;
    /** 最近使用会话的时间（UTC） */
    @TableField("last_used_at")
    private LocalDateTime lastUsedAt;
    /** 最近轮换Refresh Token的时间（UTC） */
    @TableField("last_rotated_at")
    private LocalDateTime lastRotatedAt;
    /** Refresh Token轮换次数 */
    @TableField("rotation_count")
    private Integer rotationCount;
    /** 创建时的用户会话版本快照 */
    @TableField("session_version")
    private Integer sessionVersion;
    /** 会话撤销时间（UTC）；NULL为未撤销 */
    @TableField("revoked_at")
    private LocalDateTime revokedAt;
    /** 撤销原因：LOGOUT/REUSE/ADMIN/PASSWORD_CHANGE等 */
    @TableField("revoke_reason")
    private String revokeReason;
}

