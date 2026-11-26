package com.wanyue.cloud.modules.sys.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

/** 系统用户主表更新请求：需携带读取时获取的 version。 */
@Data
public class UserUpdateReqVO {

    @NotNull(message = "版本号不能为空")
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

