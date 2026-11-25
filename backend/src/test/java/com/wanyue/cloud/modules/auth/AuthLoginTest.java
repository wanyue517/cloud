package com.wanyue.cloud.modules.auth;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.auth.dataobject.IdentityDO;
import com.wanyue.cloud.modules.auth.dataobject.PasswordCredentialDO;
import com.wanyue.cloud.modules.auth.dataobject.UserSecurityDO;
import com.wanyue.cloud.modules.auth.service.IdentityService;
import com.wanyue.cloud.modules.auth.service.PasswordCredentialService;
import com.wanyue.cloud.modules.auth.service.UserSecurityService;
import com.wanyue.cloud.modules.auth.service.impl.AuthServiceImpl;
import com.wanyue.cloud.modules.auth.vo.EmailLoginReqVO;
import com.wanyue.cloud.modules.auth.vo.UserLoginRespVO;
import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import com.wanyue.cloud.modules.sys.service.UserProfileService;
import com.wanyue.cloud.modules.sys.service.UserSettingService;
import com.wanyue.cloud.modules.sys.dataobject.UserProfileDO;
import com.wanyue.cloud.modules.sys.dataobject.UserSettingDO;
import com.wanyue.cloud.modules.sys.dataobject.UserDO;
import com.wanyue.cloud.modules.sys.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthLoginTest {
    private final AuthServiceImpl service = new AuthServiceImpl();
    private final IdentityService identities = mock(IdentityService.class);
    private final PasswordCredentialService credentials = mock(PasswordCredentialService.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final UserService users = mock(UserService.class);
    private final UserSecurityService security = mock(UserSecurityService.class);
    private final UserProfileService profiles = mock(UserProfileService.class);
    private final UserSettingService settings = mock(UserSettingService.class);
    private final JwtTokenProvider tokens = mock(JwtTokenProvider.class);
    private final UserDO user = new UserDO();
    private final UserSecurityDO userSecurity = new UserSecurityDO();
    private final IdentityDO identity = new IdentityDO();
    private final PasswordCredentialDO credential = new PasswordCredentialDO();
    private final EmailLoginReqVO request = new EmailLoginReqVO();

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "identityService", identities);
        ReflectionTestUtils.setField(service, "passwordCredentialService", credentials);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(service, "userService", users);
        ReflectionTestUtils.setField(service, "userSecurityService", security);
        ReflectionTestUtils.setField(service, "userProfileService", profiles);
        ReflectionTestUtils.setField(service, "userSettingService", settings);
        ReflectionTestUtils.setField(service, "jwtTokenProvider", tokens);
        identity.setId(10L);
        identity.setUserId(20L);
        identity.setVerifiedAt(LocalDateTime.now(ZoneOffset.UTC));
        credential.setId(30L);
        credential.setUserId(20L);
        credential.setPasswordHash("hash");
        user.setId(20L);
        user.setStatus(1);
        userSecurity.setUserId(20L);
        userSecurity.setSessionVersion(0);
        userSecurity.setPasswordLoginEnabled(true);
        when(identities.getByEmail("user@example.com")).thenReturn(identity);
        when(credentials.getByUserId(20L)).thenReturn(credential);
        when(users.getById(20L)).thenReturn(user);
        when(security.getOne(any())).thenReturn(userSecurity);
        when(encoder.matches("password", "hash")).thenReturn(true);
        when(tokens.createAccessToken(20L, 0)).thenReturn("signed-jwt-token");
        when(tokens.getAccessTokenTtlSeconds()).thenReturn(900L);
        request.setEmail(" User@Example.com ");
        request.setPassword("password");
    }

    @Test
    void loginLooksUpCredentialByUserIdAndReturnsDefinedResponse() {
        when(encoder.matches("password", "hash")).thenReturn(false, true);
        assertEquals(401, assertThrows(BizException.class, () -> service.loginWithEmail(request)).getCode());
        UserLoginRespVO response = service.loginWithEmail(request);
        assertEquals("signed-jwt-token", response.getAccessToken());
        assertEquals(900L, response.getExpiresIn());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(20L, response.getUser().getId());
        assertNull(response.getRefreshToken());
        verify(credentials, times(2)).getByUserId(20L);
        verify(credentials, never()).getById(any());
    }

    @Test
    void returnsAvailableProfileAndSettingsWithoutCredentialFields() {
        UserProfileDO profile = new UserProfileDO();
        profile.setUserId(20L);
        profile.setBio("Profile");
        UserSettingDO setting = new UserSettingDO();
        setting.setUserId(20L);
        setting.setTheme("dark");
        when(profiles.getOne(any())).thenReturn(profile);
        when(settings.getOne(any())).thenReturn(setting);
        UserLoginRespVO response = service.loginWithEmail(request);
        assertEquals("Profile", response.getUserProfile().getBio());
        assertEquals("dark", response.getUserSetting().getTheme());
        verify(tokens).createAccessToken(20L, 0);
    }

    @Test
    void missingAccountAndMissingCredentialUseSameError() {
        when(identities.getByEmail("user@example.com")).thenReturn(null, identity);
        String missingAccount = assertThrows(BizException.class, () -> service.loginWithEmail(request)).getMessage();
        when(credentials.getByUserId(20L)).thenReturn(null);
        assertEquals(missingAccount, assertThrows(BizException.class, () -> service.loginWithEmail(request)).getMessage());
    }

    @Test
    void disabledOrFrozenUserIsRejectedBeforePasswordCheck() {
        for (int status : new int[]{0, 2}) {
            user.setStatus(status);
            assertEquals(403, assertThrows(BizException.class, () -> service.loginWithEmail(request)).getCode());
        }
        verifyNoInteractions(encoder);
    }

    @Test
    void securityPolicyBlocksDisabledPasswordLoginAndActiveLock() {
        userSecurity.setPasswordLoginEnabled(false);
        assertEquals(403, assertThrows(BizException.class, () -> service.loginWithEmail(request)).getCode());
        userSecurity.setPasswordLoginEnabled(true);
        userSecurity.setLockedUntil(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1));
        assertEquals(429, assertThrows(BizException.class, () -> service.loginWithEmail(request)).getCode());
        verifyNoInteractions(encoder);
    }

    @Test
    void unverifiedEmailResetRequirementAndMfaAreNotBypassed() {
        identity.setVerifiedAt(null);
        assertEquals(403, assertThrows(BizException.class, () -> service.loginWithEmail(request)).getCode());
        identity.setVerifiedAt(LocalDateTime.now(ZoneOffset.UTC));
        credential.setResetRequired(true);
        assertEquals(403, assertThrows(BizException.class, () -> service.loginWithEmail(request)).getCode());
        credential.setResetRequired(false);
        userSecurity.setMfaRequired(true);
        assertEquals(403, assertThrows(BizException.class, () -> service.loginWithEmail(request)).getCode());
    }
}
