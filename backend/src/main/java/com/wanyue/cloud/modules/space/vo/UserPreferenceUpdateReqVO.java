package com.wanyue.cloud.modules.space.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

/** 用户空间导航偏好；默认入口仍存sys_user_setting更新请求：需携带读取时获取的 version。 */
@Data
public class UserPreferenceUpdateReqVO {

    @NotNull(message = "版本号不能为空")
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

