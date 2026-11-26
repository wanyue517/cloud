package com.wanyue.cloud.modules.auth.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

/** 多因素认证一次性恢复码表更新请求：需携带读取时获取的 version。 */
@Data
public class RecoveryCodeUpdateReqVO {

    @NotNull(message = "版本号不能为空")
    private Integer version;
    /** 所属用户ID，关联sys_user.id */
    private Long userId;
    /** 相关认证器ID；NULL表示账户级恢复码 */
    private Long factorId;
    /** 恢复码使用时间（UTC）；NULL为未使用 */
    private LocalDateTime usedAt;
}

