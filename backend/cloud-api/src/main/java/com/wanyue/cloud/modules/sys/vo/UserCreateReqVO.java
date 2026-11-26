package com.wanyue.cloud.modules.sys.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 系统用户主表新增请求：敏感认证字段不通过通用 CRUD 处理。 */
@Data
public class UserCreateReqVO {
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

