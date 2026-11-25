package com.wanyue.cloud.modules.auth;
import com.wanyue.cloud.modules.auth.service.RegistrationPolicy;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.auth.service.PasswordCredentialService;
import com.wanyue.cloud.modules.auth.service.UserSecurityService;
import com.wanyue.cloud.modules.auth.service.IdentityService;
import com.wanyue.cloud.modules.auth.service.SecurityLogService;
import com.wanyue.cloud.modules.auth.service.UserRoleService;
import com.wanyue.cloud.modules.auth.service.VerificationCodeService;
import com.wanyue.cloud.modules.auth.service.impl.AuthServiceImpl;
import com.wanyue.cloud.modules.auth.vo.EmailRegisterReqVO;
import com.wanyue.cloud.modules.auth.enums.VerificationSceneEnum;
import com.wanyue.cloud.modules.sys.dataobject.UserDO;
import com.wanyue.cloud.modules.sys.service.UserService;
import com.wanyue.cloud.modules.sys.service.UserProfileService;
import com.wanyue.cloud.modules.sys.service.UserSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AuthRegistrationTest {
    private final AuthServiceImpl service = new AuthServiceImpl();
    private final IdentityService identities = mock(IdentityService.class);
    private final VerificationCodeService codes = mock(VerificationCodeService.class);
    private final UserService users = mock(UserService.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "identityService", identities);
        ReflectionTestUtils.setField(service, "verificationCodeService", codes);
        ReflectionTestUtils.setField(service, "userService", users);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        UserProfileService profiles = mock(UserProfileService.class);
        when(profiles.save(any())).thenReturn(true);
        ReflectionTestUtils.setField(service, "userProfileService", profiles);
        UserSettingService settings = mock(UserSettingService.class);
        when(settings.save(any())).thenReturn(true);
        ReflectionTestUtils.setField(service, "userSettingService", settings);
        PasswordCredentialService credentials = mock(PasswordCredentialService.class);
        when(credentials.save(any())).thenReturn(true);
        ReflectionTestUtils.setField(service, "passwordCredentialService", credentials);
        UserSecurityService security = mock(UserSecurityService.class);
        when(security.save(any())).thenReturn(true);
        ReflectionTestUtils.setField(service, "userSecurityService", security);
        ReflectionTestUtils.setField(service, "userRoleService", mock(UserRoleService.class));
        var policy=mock(RegistrationPolicy.class);
        when(policy.claimRole()).thenReturn("USER");
        ReflectionTestUtils.setField(service,"registrationPolicy",policy);
        ReflectionTestUtils.setField(service, "securityLogService", mock(SecurityLogService.class));
        var spaces = mock(com.wanyue.cloud.modules.space.service.SpaceService.class);
        when(spaces.save(any())).thenReturn(true);
        ReflectionTestUtils.setField(service, "spaceService", spaces);
        var members = mock(com.wanyue.cloud.modules.space.service.MemberService.class);
        when(members.save(any())).thenReturn(true);
        ReflectionTestUtils.setField(service, "memberService", members);
        when(encoder.encode(anyString())).thenReturn("hashed-password");
        when(users.save(any(UserDO.class))).thenAnswer(invocation -> {
            UserDO user = invocation.getArgument(0);
            user.setId(123L);
            return true;
        });
    }

    private EmailRegisterReqVO request(String email) {
        EmailRegisterReqVO request = new EmailRegisterReqVO();
        request.setEmail(email);
        request.setCode("012345");
        request.setPassword("password123");
        return request;
    }

    @ParameterizedTest
    @ValueSource(strings = {"Alice", "alice+work", "abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz01"})
    void derivesDisplayNameFromNormalizedEmailPrefix(String prefix) {
        String email = prefix.toLowerCase(java.util.Locale.ROOT) + "@example.com";
        assertTrue(service.registerWithEmail(request(" " + prefix + "@Example.com ")));
        var order = inOrder(codes, users, identities);
        order.verify(identities).checkEmail(email);
        order.verify(codes).reserveInTransaction(VerificationSceneEnum.EMAIL_REGISTER, email, "012345");
        order.verify(users).save(argThat((UserDO user) -> user.getDisplayName().equals(
                prefix.toLowerCase(java.util.Locale.ROOT))));
        order.verify(identities).bindEmail(123L, email);
        verify(encoder).encode("password123");
    }

    @Test
    void invalidCodeDoesNotCreateUser() {
        doThrow(new BizException(HttpStatus.BAD_REQUEST, 400, "验证码错误"))
                .when(codes).reserveInTransaction(any(), anyString(), anyString());
        assertThrows(BizException.class, () -> service.registerWithEmail(request("alice@example.com")));
        verifyNoInteractions(users, encoder);
        verify(identities, never()).bindEmail(anyLong(), anyString());
    }

    @Test
    void failedUserSaveStopsRegistrationBeforeWritingAssociatedData() {
        when(users.save(any(UserDO.class))).thenReturn(false);
        assertEquals(409, assertThrows(BizException.class,
                () -> service.registerWithEmail(request("alice@example.com"))).getCode());
        verify(identities, never()).bindEmail(anyLong(), anyString());
        verifyNoInteractions(encoder);
    }
}
