package com.wanyue.cloud.modules.sys.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

/** 用户扩展资料表更新请求：需携带读取时获取的 version。 */
@Data
public class UserProfileUpdateReqVO {

    @NotNull(message = "版本号不能为空")
    private Integer version;
    /** 所属用户ID，关联sys_user.id */
    private Long userId;
    /** 个人简介 */
    private String bio;
    /** 个人网站地址 */
    private String websiteUrl;
    /** 个人主页封面地址 */
    private String coverUrl;
}

