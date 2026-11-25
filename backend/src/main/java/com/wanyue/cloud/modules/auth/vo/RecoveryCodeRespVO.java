package com.wanyue.cloud.modules.auth.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDateTime;

/** 多因素认证一次性恢复码表响应对象：默认屏蔽常见敏感凭据字段。 */
@Data
public class RecoveryCodeRespVO {
    /** 业务主键：前端 JSON 应序列化为字符串以规避 JS 精度问题。 */
    private Long id;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
    /** 所属用户ID，关联sys_user.id */
    private Long userId;
    /** 相关认证器ID；NULL表示账户级恢复码 */
    private Long factorId;
    /** 恢复码使用时间（UTC）；NULL为未使用 */
    private LocalDateTime usedAt;
}

