package com.wanyue.cloud.modules.space.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wanyue.cloud.common.domain.BaseDO;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 用户空间导航偏好；默认入口仍存sys_user_setting（space_user_preference）持久化对象。
 * 公共字段由 BaseDO 定义，不重复生成。
 */
@Getter
@Setter
@TableName("space_user_preference")
public class UserPreferenceDO extends BaseDO {
    /** 偏好所属用户，关联sys_user.id */
    @TableField("user_id")
    private Long userId;
    /** 关联space.id；记录存在不代表有访问权限 */
    @TableField("space_id")
    private Long spaceId;
    /** 该用户的空间导航顺序；按sort_order、space_id稳定排序 */
    @TableField("sort_order")
    private Long sortOrder;
    /** 该用户是否折叠空间目录树 */
    @TableField("tree_collapsed")
    private Boolean treeCollapsed;
}

