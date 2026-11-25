package com.wanyue.cloud.modules.space.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wanyue.cloud.common.domain.BaseDO;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 共享空间成员关系，独立于文件夹授权（space_member）持久化对象。
 * 公共字段由 BaseDO 定义，不重复生成。
 */
@Getter
@Setter
@TableName("space_member")
public class MemberDO extends BaseDO {
    /** 关联space.id；只允许SHARED空间使用 */
    @TableField("space_id")
    private Long spaceId;
    /** 成员用户，关联sys_user.id */
    @TableField("user_id")
    private Long userId;
    /** OWNER/MANAGER/EDITOR/VIEWER，不引用平台auth_role */
    @TableField("role")
    private String role;
    /** 成员接受邀请或所有者创建成员关系的时间UTC */
    @TableField("joined_at")
    private LocalDateTime joinedAt;
    /** 访问到期时间UTC；NULL永久；OWNER不能到期 */
    @TableField("expires_at")
    private LocalDateTime expiresAt;
    /** 有效OWNER唯一性辅助列，不映射到DO */
    @TableField("owner_space_id")
    private Long ownerSpaceId;
}

