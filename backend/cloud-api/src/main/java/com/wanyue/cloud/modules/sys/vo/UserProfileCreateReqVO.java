package com.wanyue.cloud.modules.sys.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 用户扩展资料表新增请求：敏感认证字段不通过通用 CRUD 处理。 */
@Data
public class UserProfileCreateReqVO {
    /** 所属用户ID，关联sys_user.id */
    private Long userId;
    /** 个人简介 */
    private String bio;
    /** 个人网站地址 */
    private String websiteUrl;
    /** 个人主页封面地址 */
    private String coverUrl;
}

