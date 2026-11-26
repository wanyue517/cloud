package com.wanyue.cloud.modules.sys.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wanyue.cloud.common.domain.BaseDO;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 用户个性化设置表（sys_user_setting）持久化对象。
 * 公共字段由 BaseDO 定义，不重复生成。
 */
@Getter
@Setter
@TableName("sys_user_setting")
public class UserSettingDO extends BaseDO {
    /** 所属用户ID，关联sys_user.id */
    @TableField("user_id")
    private Long userId;
    /** 主题：system/light/dark */
    @TableField("theme")
    private String theme;
    /** 界面语言，如zh-CN、en-US */
    @TableField("locale")
    private String locale;
    /** IANA时区，如Asia/Shanghai */
    @TableField("timezone")
    private String timezone;
    /** 显示密度：comfortable/compact */
    @TableField("density")
    private String density;
    /** 文件视图：list/grid */
    @TableField("file_view")
    private String fileView;
    /** 文件默认排序规则 */
    @TableField("file_sort")
    private String fileSort;
    /** 是否展示隐藏文件：0否，1是 */
    @TableField("show_hidden_files")
    private Boolean showHiddenFiles;
    /** 默认进入的空间ID，空间删除后需清空 */
    @TableField("default_space_id")
    private Long defaultSpaceId;
    /** 消息通知偏好JSON；不存放密钥 */
    @TableField("notification_settings")
    private String notificationSettings;
}

