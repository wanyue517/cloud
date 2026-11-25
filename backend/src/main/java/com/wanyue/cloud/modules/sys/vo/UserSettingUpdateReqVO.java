package com.wanyue.cloud.modules.sys.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

/** 用户个性化设置表更新请求：需携带读取时获取的 version。 */
@Data
public class UserSettingUpdateReqVO {

    @NotNull(message = "版本号不能为空")
    private Integer version;
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

