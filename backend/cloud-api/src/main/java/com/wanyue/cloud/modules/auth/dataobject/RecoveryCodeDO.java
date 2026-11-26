package com.wanyue.cloud.modules.auth.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wanyue.cloud.common.domain.BaseDO;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 多因素认证一次性恢复码表（auth_recovery_code）持久化对象。
 * 公共字段由 BaseDO 定义，不重复生成。
 */
@Getter
@Setter
@TableName("auth_recovery_code")
public class RecoveryCodeDO extends BaseDO {
    /** 所属用户ID，关联sys_user.id */
    @TableField("user_id")
    private Long userId;
    /** 相关认证器ID；NULL表示账户级恢复码 */
    @TableField("factor_id")
    private Long factorId;
    /** 随机恢复码的HMAC-SHA256摘要，不存明文 */
    @TableField("code_hash")
    private String codeHash;
    /** 恢复码使用时间（UTC）；NULL为未使用 */
    @TableField("used_at")
    private LocalDateTime usedAt;
}

