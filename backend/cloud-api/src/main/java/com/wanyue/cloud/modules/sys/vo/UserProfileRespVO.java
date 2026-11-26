package com.wanyue.cloud.modules.sys.vo;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.time.LocalDateTime;

/** 用户扩展资料表响应对象：默认屏蔽常见敏感凭据字段。 */
@Data
public class UserProfileRespVO {
    /** 业务主键：前端 JSON 应序列化为字符串以规避 JS 精度问题。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer version;
    /** 所属用户ID，关联sys_user.id */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long userId;
    /** 个人简介 */
    private String bio;
    /** 个人网站地址 */
    private String websiteUrl;
    /** 个人主页封面地址 */
    private String coverUrl;
}

