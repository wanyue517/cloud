package com.wanyue.cloud.modules.auth.vo;

import com.wanyue.cloud.modules.sys.vo.UserProfileRespVO;
import com.wanyue.cloud.modules.sys.vo.UserRespVO;
import com.wanyue.cloud.modules.sys.vo.UserSettingRespVO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserLoginRespVO {

    /** 访问令牌 */
    private String accessToken;

    /** 刷新令牌 */
    private String refreshToken;

    /** Access Token 有效期，单位秒 */
    private Long expiresIn;

    /** Token 类型，通常为 Bearer */
    private String tokenType;

    /** 当前登录用户 */
    private UserRespVO user;

    /** 用户个人信息 */
    private UserProfileRespVO userProfile;

    /** 用户个性化设置 */
    private UserSettingRespVO userSetting;
}
