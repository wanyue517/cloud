package com.wanyue.cloud.modules.sys.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 用户个性化设置表新增请求：敏感认证字段不通过通用 CRUD 处理。 */
@Data
public class UserSettingCreateReqVO {
    /** 所属用户ID，关联sys_user.id */
    private Long userId;
    /** 主题：system/light/dark */
    private String theme;
    /** 界面语言，如zh-CN、en-US */
    private String locale;
    /** IANA时区，如Asia/Shanghai */
    private String timezone;
    /** 显示密度：comfortable/compact */
    private String density;
    /** 文件视图：list/grid */
    private String fileView;
    /** 文件默认排序规则 */
    private String fileSort;
    /** 是否展示隐藏文件：0否，1是 */
    private Boolean showHiddenFiles;
    /** 默认进入的空间ID，空间删除后需清空 */
    private Long defaultSpaceId;
    /** 消息通知偏好JSON；不存放密钥 */
    private String notificationSettings;
}

