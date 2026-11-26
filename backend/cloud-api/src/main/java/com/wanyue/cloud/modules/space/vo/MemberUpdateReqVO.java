package com.wanyue.cloud.modules.space.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

/** 共享空间成员关系，独立于文件夹授权更新请求：需携带读取时获取的 version。 */
@Data
public class MemberUpdateReqVO {

    @NotNull(message = "版本号不能为空")
    private Integer version;
    /** 关联space.id；只允许SHARED空间使用 */
    private Long spaceId;
    /** 成员用户，关联sys_user.id */
    private Long userId;
    /** OWNER/MANAGER/EDITOR/VIEWER，不引用平台auth_role */
    private String role;
    /** 成员接受邀请或所有者创建成员关系的时间UTC */
    private LocalDateTime joinedAt;
    /** 访问到期时间UTC；NULL永久；OWNER不能到期 */
    private LocalDateTime expiresAt;
    /** 有效OWNER唯一性辅助列，不映射到DO */
    private Long ownerSpaceId;
}

