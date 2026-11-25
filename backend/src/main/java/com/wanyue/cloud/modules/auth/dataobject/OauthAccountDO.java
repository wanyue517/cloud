package com.wanyue.cloud.modules.auth.dataobject;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wanyue.cloud.common.domain.BaseDO;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 第三方OAuth账号绑定表（auth_oauth_account）持久化对象。
 * 公共字段由 BaseDO 定义，不重复生成。
 */
@Getter
@Setter
@TableName("auth_oauth_account")
public class OauthAccountDO extends BaseDO {
    /** 所属用户ID，关联sys_user.id */
    @TableField("user_id")
    private Long userId;
    /** 第三方平台编码：github/google/wechat等 */
    @TableField("provider")
    private String provider;
    /** 第三方应用标识；区分微信等多应用场景 */
    @TableField("provider_app_id")
    private String providerAppId;
    /** 第三方用户唯一标识，大小写敏感 */
    @TableField("provider_subject")
    private String providerSubject;
    /** 平台跨应用联合标识，如微信UnionID */
    @TableField("provider_union_id")
    private String providerUnionId;
    /** 绑定时第三方账号显示名称 */
    @TableField("provider_name")
    private String providerName;
    /** 第三方账号头像地址 */
    @TableField("provider_avatar_url")
    private String providerAvatarUrl;
    /** 最近使用该第三方账号登录的时间（UTC） */
    @TableField("last_used_at")
    private LocalDateTime lastUsedAt;
}

