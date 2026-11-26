package com.wanyue.cloud.modules.auth;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.mail.MailService;
import com.wanyue.cloud.modules.auth.enums.VerificationSceneEnum;
import com.wanyue.cloud.modules.auth.sender.EmailVerificationCodeSender;
import com.wanyue.cloud.modules.auth.service.IdentityService;
import com.wanyue.cloud.modules.auth.service.VerificationCodeService;
import com.wanyue.cloud.modules.auth.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthEmailTest {
    @Test
    void registrationEmailActuallyDispatchesAndRejectsExistingIdentity() {
        AuthServiceImpl auth = new AuthServiceImpl();
        IdentityService identities = mock(IdentityService.class);
        VerificationCodeService codes = mock(VerificationCodeService.class);
        ReflectionTestUtils.setField(auth, "identityService", identities);
        ReflectionTestUtils.setField(auth, "verificationCodeService", codes);
        when(identities.checkEmail("user@example.com")).thenReturn(false, true);
        auth.sendEmailCode(" User@Example.com ");
        verify(codes).send(VerificationSceneEnum.EMAIL_REGISTER, "user@example.com");
        assertEquals(409, assertThrows(BizException.class,
                () -> auth.sendEmailCode("user@example.com")).getCode());
        verifyNoMoreInteractions(codes);
    }

    @Test
    void emailIncludesPurposeExpiryAndCode() {
        MailService mail = mock(MailService.class);
        EmailVerificationCodeSender sender = new EmailVerificationCodeSender(mail, "from@example.com");
        sender.send(VerificationSceneEnum.EMAIL_REGISTER, "user@example.com", "012345", 300);
        verify(mail).sendText(eq("from@example.com"), eq("user@example.com"), anyString(),
                argThat(text -> text.contains("注册账号") && text.contains("012345") && text.contains("300")));
        assertThrows(IllegalArgumentException.class, () -> sender.send(
                VerificationSceneEnum.SMS_LOGIN, "user@example.com", "012345", 300));
        verifyNoMoreInteractions(mail);
    }
}
