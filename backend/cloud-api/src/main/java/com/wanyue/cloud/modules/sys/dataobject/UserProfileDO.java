package com.wanyue.cloud.modules.sys.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wanyue.cloud.common.domain.BaseDO;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 用户扩展资料表（sys_user_profile）持久化对象。
 * 公共字段由 BaseDO 定义，不重复生成。
 */
@Getter
@Setter
@TableName("sys_user_profile")
public class UserProfileDO extends BaseDO {
    /** 所属用户ID，关联sys_user.id */
    @TableField("user_id")
    private Long userId;
    /** 个人简介 */
    @TableField("bio")
    private String bio;
    /** 个人网站地址 */
    @TableField("website_url")
    private String websiteUrl;
    /** 个人主页封面地址 */
    @TableField("cover_url")
    private String coverUrl;
}

