package com.wanyue.cloud.modules.auth.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wanyue.cloud.modules.auth.mapper.IdentityMapper;
import com.wanyue.cloud.modules.auth.dataobject.IdentityDO;
import com.wanyue.cloud.modules.auth.dataobject.PasswordCredentialDO;
import com.wanyue.cloud.modules.auth.dataobject.UserSecurityDO;
import com.wanyue.cloud.modules.auth.enums.IdentityTypeEnum;
import com.wanyue.cloud.modules.auth.enums.UserRoleEnum;
import com.wanyue.cloud.modules.auth.enums.UserStatusEnum;
import com.wanyue.cloud.modules.auth.enums.VerificationSceneEnum;
import com.wanyue.cloud.modules.auth.service.*;
import com.wanyue.cloud.modules.auth.vo.*;
import com.wanyue.cloud.modules.space.dataobject.MemberDO;
import com.wanyue.cloud.modules.space.dataobject.SpaceDO;
import com.wanyue.cloud.modules.space.enums.SpaceKindEnum;
import com.wanyue.cloud.modules.space.enums.SpaceMemberRoleEnum;
import com.wanyue.cloud.modules.space.service.MemberService;
import com.wanyue.cloud.modules.space.service.SpaceService;
import com.wanyue.cloud.modules.sys.dataobject.UserDO;
import com.wanyue.cloud.modules.sys.dataobject.UserProfileDO;
import com.wanyue.cloud.modules.sys.dataobject.UserSettingDO;
import com.wanyue.cloud.modules.sys.service.UserService;
import com.wanyue.cloud.modules.sys.service.UserProfileService;
import com.wanyue.cloud.modules.sys.service.UserSettingService;
import jakarta.annotation.Resource;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.auth.support.EmailNormalizer;
import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import com.wanyue.cloud.modules.sys.vo.UserRespVO;
import com.wanyue.cloud.modules.sys.vo.UserProfileRespVO;
import com.wanyue.cloud.modules.sys.vo.UserSettingRespVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 认证业务编排；注册事务通过各业务 Service 写入关联数据。
 */
@Service
public class AuthServiceImpl extends ServiceImpl<IdentityMapper, IdentityDO> implements AuthService {

    @Value("${cloud.auth.default-space-name}")
    private String defaultSpaceName;

    @Value("${cloud.auth.default-space-icon}")
    private String defaultSpaceIcon;

    @Value("${cloud.auth.default-space-color}")
    private String defaultSpaceColor;

    @Value("${cloud.auth.default-space-description}")
    private String defaultSpaceDescription;

    @Resource
    private IdentityService identityService;

    @Resource
    private VerificationCodeService verificationCodeService;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private UserService userService;

    @Resource
    private UserProfileService userProfileService;

    @Resource
    private UserSettingService userSettingService;

    @Resource
    private PasswordCredentialService passwordCredentialService;

    @Resource
    private UserSecurityService userSecurityService;

    @Resource
    private UserRoleService userRoleService;

    @Resource
    private SecurityLogService securityLogService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private MemberService memberService;

    @Resource
    private JwtTokenProvider jwtTokenProvider;

    @Override
    public UserLoginRespVO loginWithEmail(EmailLoginReqVO request) {
        // 1. 规范化邮箱：去除首尾空格、转小写，与注册时的保存规则保持一致。
        String email = EmailNormalizer.normalize(request.getEmail());

        // 2. 查询已验证的邮箱身份；查询不到时与密码错误使用相同提示。
        IdentityDO identity = identityService.getByEmail(email);
        if (identity == null) {
            throw BizException.unauthorized("邮箱或密码错误");
        }
        if (identity.getVerifiedAt() == null) {
            throw new BizException(HttpStatus.FORBIDDEN, 403, "邮箱尚未验证");
        }

        // 3. 查询用户主体，确认账号存在且未被禁用或冻结。
        UserDO user = userService.getById(identity.getUserId());
        if (user == null) {
            throw BizException.unauthorized("邮箱或密码错误");
        }
        if (!UserStatusEnum.NORMAL.getCode().equals(user.getStatus())) {
            throw new BizException(HttpStatus.FORBIDDEN, 403, "账号已被禁用或冻结");
        }

        // 4. 检查账号安全策略：是否允许密码登录、当前是否处于临时锁定期。
        UserSecurityDO userSecurity = userSecurityService.getOne(Wrappers.<UserSecurityDO>lambdaQuery()
                .eq(UserSecurityDO::getUserId, user.getId()));
        if (userSecurity == null) {
            throw BizException.conflict("账号安全信息不存在");
        }
        if (!Boolean.TRUE.equals(userSecurity.getPasswordLoginEnabled())) {
            throw new BizException(HttpStatus.FORBIDDEN, 403, "账号未开启密码登录");
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (userSecurity.getLockedUntil() != null && userSecurity.getLockedUntil().isAfter(now)) {
            throw new BizException(HttpStatus.TOO_MANY_REQUESTS, 429, "账号暂时锁定，请稍后重试");
        }

        // 5. 按用户ID查询密码凭证；凭证自身的主键不是用户ID。
        PasswordCredentialDO passwordCredential = passwordCredentialService.getByUserId(identity.getUserId());
        if (passwordCredential == null || passwordCredential.getPasswordHash() == null
                || passwordCredential.getPasswordHash().isBlank()) {
            throw BizException.unauthorized("邮箱或密码错误");
        }

        // 6. 使用PasswordEncoder校验原始密码，不能对密码去空格或直接比较哈希字符串。
        if (!passwordEncoder.matches(request.getPassword(), passwordCredential.getPasswordHash())) {
            // TODO：原子累加失败次数、按策略锁定账号，并记录失败日志。
            // 该部分需要保证失败计数不随认证异常回滚，后续单独补充事务处理。
            throw BizException.unauthorized("邮箱或密码错误");
        }

        // 7. 检查后续验证要求。重置密码、MFA流程尚未接入，先阻止直接登录。
        if (Boolean.TRUE.equals(passwordCredential.getResetRequired())) {
            throw new BizException(HttpStatus.FORBIDDEN, 403, "请先重置密码后再登录");
        }
        if (Boolean.TRUE.equals(userSecurity.getMfaRequired())) {
            // TODO：生成短期MFA验证凭证，完成第二步验证后再签发正式令牌。
            throw new BizException(HttpStatus.FORBIDDEN, 403, "账号需要多因素验证，暂不支持直接登录");
        }

        // 8. 签发带过期时间的JWT访问令牌；刷新令牌和auth_session保存待接入。
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), userSecurity.getSessionVersion());

        // 9. 查询已有的个人资料和设置并转换为响应VO，不向外返回凭证对象。
        UserRespVO userResp = new UserRespVO();
        BeanUtils.copyProperties(user, userResp);
        UserProfileDO profile = userProfileService.getOne(Wrappers.<UserProfileDO>lambdaQuery()
                .eq(UserProfileDO::getUserId, user.getId()));
        UserProfileRespVO profileResp = null;
        if (profile != null) {
            profileResp = new UserProfileRespVO();
            BeanUtils.copyProperties(profile, profileResp);
        }
        UserSettingDO setting = userSettingService.getOne(Wrappers.<UserSettingDO>lambdaQuery()
                .eq(UserSettingDO::getUserId, user.getId()));
        UserSettingRespVO settingResp = null;
        if (setting != null) {
            settingResp = new UserSettingRespVO();
            BeanUtils.copyProperties(setting, settingResp);
        }

        // 10. 使用现有返回类，refreshToken尚未实现，保持null。
        // TODO：接入会话后再补充失败计数清零、最近登录时间和成功审计日志。
        return UserLoginRespVO.builder()
                .accessToken(accessToken)
                .expiresIn(jwtTokenProvider.getAccessTokenTtlSeconds())
                .tokenType("Bearer")
                .user(userResp)
                .userProfile(profileResp)
                .userSetting(settingResp)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean registerWithEmail(EmailRegisterReqVO request) {
        String email = EmailNormalizer.normalize(request.getEmail());
        // 1. 验证系统是否开放注册
        // 2. 验证邮箱是否已注册
        Boolean exist = identityService.checkEmail(email);
        if(exist){
            throw BizException.conflict("邮箱已注册");
        }
        // 4. 验证邮箱验证码
        verificationCodeService.reserveInTransaction(
                VerificationSceneEnum.EMAIL_REGISTER,
                email,
                request.getCode()
        );

        // 6. 创建用户主体（sys_user）
        UserDO user = new UserDO();
        // 邮箱前缀作为初始显示名称；不接受客户端指定注册用户名。
        user.setDisplayName(email.substring(0, email.lastIndexOf('@')));
        user.setStatus(UserStatusEnum.NORMAL.getCode());
        requireSaved(userService.save(user));

        // 7. 创建用户个人资料（sys_user_profile）
        UserProfileDO userProfile = new UserProfileDO();
        userProfile.setUserId(user.getId());
        requireSaved(userProfileService.save(userProfile));

        // 8. 初始化用户偏好配置（sys_user_setting）
        UserSettingDO userSetting = new UserSettingDO();
        userSetting.setUserId(user.getId());
        requireSaved(userSettingService.save(userSetting));

        // 9. 绑定已验证邮箱身份（auth_identity）
        identityService.bindEmail(user.getId(), email);

        // 10. 创建密码凭证（auth_password_credential）
        String hashPwd = passwordEncoder.encode(request.getPassword());

        PasswordCredentialDO passwordCredential = new PasswordCredentialDO();
        passwordCredential.setUserId(user.getId());
        passwordCredential.setPasswordHash(hashPwd);
        requireSaved(passwordCredentialService.save(passwordCredential));

        // 11. 初始化账号安全信息（auth_user_security）
        UserSecurityDO userSecurity = new UserSecurityDO();
        userSecurity.setUserId(user.getId());
        requireSaved(userSecurityService.save(userSecurity));

        // 12. 分配默认用户角色（auth_user_role）
        // 自助注册仅分配普通用户角色。
        userRoleService.bindRole(user.getId(), UserRoleEnum.USER.getCode());

        /// 14. 记录注册安全日志（auth_security_log）
        securityLogService.recordRegisterSuccess(
                user.getId(),
                IdentityTypeEnum.EMAIL.getCode()
        );

        // 15. 触发用户网盘空间初始化
        SpaceDO space = new SpaceDO();
        space.setOwnerUserId(user.getId());
        space.setKind(SpaceKindEnum.PERSONAL.getCode());
        space.setName(defaultSpaceName);
        space.setIcon(defaultSpaceIcon);
        space.setColor(defaultSpaceColor);
        space.setDescription(defaultSpaceDescription);
        spaceService.save(space);

        MemberDO member = new MemberDO();
        member.setSpaceId(space.getId());
        member.setUserId(user.getId());
        member.setRole(SpaceMemberRoleEnum.OWNER.getValue());
        memberService.save(member);

        // 16. 返回注册结果
        return true;
    }

    @Override
    public void sendEmailCode(String email) {
        String normalized = EmailNormalizer.normalize(email);
        if (identityService.checkEmail(normalized)) {
            throw BizException.conflict("邮箱已注册");
        }
        verificationCodeService.send(VerificationSceneEnum.EMAIL_REGISTER, normalized);
    }

    private void requireSaved(boolean saved) {
        if (!saved) {
            throw BizException.conflict("注册数据保存失败");
        }
    }
}

