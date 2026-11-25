package com.wanyue.cloud.modules.sys.vo;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.time.LocalDateTime;

/** 用户个性化设置表响应对象：默认屏蔽常见敏感凭据字段。 */
@Data
public class UserSettingRespVO {
    /** 业务主键：前端 JSON 应序列化为字符串以规避 JS 精度问题。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
    /** 所属用户ID，关联sys_user.id */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
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
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long defaultSpaceId;
    /** 消息通知偏好JSON；不存放密钥 */
    private String notificationSettings;
}

