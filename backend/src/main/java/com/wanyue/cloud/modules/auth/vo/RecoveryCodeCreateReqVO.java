package com.wanyue.cloud.modules.auth.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 多因素认证一次性恢复码表新增请求：敏感认证字段不通过通用 CRUD 处理。 */
@Data
public class RecoveryCodeCreateReqVO {
    /** 所属用户ID，关联sys_user.id */
    private Long userId;
    /** 相关认证器ID；NULL表示账户级恢复码 */
    private Long factorId;
    /** 恢复码使用时间（UTC）；NULL为未使用 */
    private LocalDateTime usedAt;
}

