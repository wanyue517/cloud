package com.wanyue.cloud.modules.space.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.time.LocalDateTime;

/** 用户空间导航偏好；默认入口仍存sys_user_setting响应对象：默认屏蔽常见敏感凭据字段。 */
@Data
public class UserPreferenceRespVO {
    /** 业务主键：前端 JSON 应序列化为字符串以规避 JS 精度问题。 */
    private Long id;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
    /** 偏好所属用户，关联sys_user.id */
    private Long userId;
    /** 关联space.id；记录存在不代表有访问权限 */
    private Long spaceId;
    /** 该用户的空间导航顺序；按sort_order、space_id稳定排序 */
    private Long sortOrder;
    /** 该用户是否折叠空间目录树 */
    private Boolean treeCollapsed;
}

