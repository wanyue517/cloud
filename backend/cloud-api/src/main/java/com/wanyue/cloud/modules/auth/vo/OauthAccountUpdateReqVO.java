package com.wanyue.cloud.modules.auth.vo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

/** 第三方OAuth账号绑定表更新请求：需携带读取时获取的 version。 */
@Data
public class OauthAccountUpdateReqVO {

    @NotNull(message = "版本号不能为空")
    private Integer version;
    /** 所属用户ID，关联sys_user.id */
    private Long userId;
    /** 第三方平台编码：github/google/wechat等 */
    private String provider;
    /** 第三方应用标识；区分微信等多应用场景 */
    private String providerAppId;
    /** 第三方用户唯一标识，大小写敏感 */
    private String providerSubject;
    /** 平台跨应用联合标识，如微信UnionID */
    private String providerUnionId;
    /** 绑定时第三方账号显示名称 */
    private String providerName;
    /** 第三方账号头像地址 */
    private String providerAvatarUrl;
    /** 最近使用该第三方账号登录的时间（UTC） */
    private LocalDateTime lastUsedAt;
}

