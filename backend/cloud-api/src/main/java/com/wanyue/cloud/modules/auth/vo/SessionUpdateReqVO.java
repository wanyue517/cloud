package com.wanyue.cloud.modules.auth.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

/** 用户登录会话与Refresh Token表更新请求：需携带读取时获取的 version。 */
@Data
public class SessionUpdateReqVO {

    @NotNull(message = "版本号不能为空")
    private Integer version;
    /** 所属用户ID，关联sys_user.id */
    private Long userId;
    /** Refresh Token到期时间（UTC） */
    private LocalDateTime refreshExpiresAt;
    /** 客户端生成的设备标识；不作为安全认证凭据 */
    private String deviceId;
    /** 设备显示名称 */
    private String deviceName;
    /** 客户端类型：WEB/DESKTOP/ANDROID/IOS等 */
    private String clientType;
    /** 最近会话客户端User-Agent */
    private String userAgent;
    /** 最近使用的IPv4或IPv6地址 */
    private String ipAddress;
    /** 最近使用会话的时间（UTC） */
    private LocalDateTime lastUsedAt;
    /** 最近轮换Refresh Token的时间（UTC） */
    private LocalDateTime lastRotatedAt;
    /** Refresh Token轮换次数 */
    private Integer rotationCount;
    /** 创建时的用户会话版本快照 */
    private Integer sessionVersion;
    /** 会话撤销时间（UTC）；NULL为未撤销 */
    private LocalDateTime revokedAt;
    /** 撤销原因：LOGOUT/REUSE/ADMIN/PASSWORD_CHANGE等 */
    private String revokeReason;
}

