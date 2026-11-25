package com.wanyue.cloud.modules.sys.vo;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.time.LocalDateTime;

/** 系统用户主表响应对象：默认屏蔽常见敏感凭据字段。 */
@Data
public class UserRespVO {
    /** 业务主键：前端 JSON 应序列化为字符串以规避 JS 精度问题。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
    /** 用户显示名称 */
    private String displayName;
    /** 用户头像资源地址 */
    private String avatarUrl;
    /** 用户状态：0禁用，1正常，2冻结 */
    private Integer status;
    /** 注册来源：PASSWORD/OAUTH/INVITE等 */
    private String registerSource;
    /** 账号注册时间（UTC） */
    private LocalDateTime registeredAt;
    /** 最近成功登录时间（UTC） */
    private LocalDateTime lastLoginAt;
}

