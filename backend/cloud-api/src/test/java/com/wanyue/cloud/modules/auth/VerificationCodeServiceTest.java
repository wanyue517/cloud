package com.wanyue.cloud.modules.auth;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.auth.config.VerificationProperties;
import com.wanyue.cloud.modules.auth.enums.VerificationChannelEnum;
import com.wanyue.cloud.modules.auth.enums.VerificationSceneEnum;
import com.wanyue.cloud.modules.auth.sender.VerificationCodeSender;
import com.wanyue.cloud.modules.auth.service.VerificationRateLimiter;
import com.wanyue.cloud.modules.auth.service.impl.VerificationCodeServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VerificationCodeServiceTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final VerificationCodeSender sender = mock(VerificationCodeSender.class);
    private final VerificationRateLimiter limiter = mock(VerificationRateLimiter.class);
    private final VerificationCodeServiceImpl service = new VerificationCodeServiceImpl();
    private final List<Call> calls = new ArrayList<>();
    private long result = 1;

    record Call(String script, List<String> keys, Object[] args) {}

    @BeforeEach
    void setUp() {
        VerificationProperties properties = new VerificationProperties();
        properties.setHmacSecret("test-only-secret-with-at-least-32-characters");
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(service, "verificationCodeSenders", List.of(sender));
        ReflectionTestUtils.setField(service, "rateLimiter", limiter);
        when(sender.channel()).thenReturn(VerificationChannelEnum.EMAIL);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenAnswer(invocation -> {
                    RedisScript<?> script = invocation.getArgument(0);
                    Object[] raw = invocation.getRawArguments();
                    calls.add(new Call(script.getScriptAsString(), invocation.getArgument(1), (Object[]) raw[2]));
                    return result;
                });
    }

    @AfterEach
    void cleanup() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void sendsNormalizedEmailAndStoresOnlyDigest() {
        service.send(VerificationSceneEnum.EMAIL_REGISTER, " User@Example.com ");
        verify(sender).send(eq(VerificationSceneEnum.EMAIL_REGISTER), eq("user@example.com"),
                matches("[0-9]{6}"), eq(300));
        assertEquals(1, calls.size());
        assertTrue(calls.get(0).args()[0].toString().matches("[0-9a-f]{64}"));
        assertFalse(calls.get(0).keys().get(0).contains("example.com"));
        verify(limiter).checkTarget(matches("[0-9a-f]{64}"));
    }

    @Test
    void rateLimitedIssueDoesNotSendMail() {
        result = -1;
        BizException error = assertThrows(BizException.class,
                () -> service.send(VerificationSceneEnum.EMAIL_REGISTER, "user@example.com"));
        assertEquals(429, error.getCode());
        verify(sender, never()).send(any(), anyString(), anyString(), anyInt());
    }

    @Test
    void mailFailureCancelsOnlyItsOwnIssue() {
        doThrow(new IllegalStateException("test SMTP failure")).when(sender)
                .send(any(), anyString(), anyString(), anyInt());
        BizException error = assertThrows(BizException.class,
                () -> service.send(VerificationSceneEnum.EMAIL_REGISTER, "user@example.com"));
        assertEquals(503, error.getCode());
        assertEquals(2, calls.size());
        assertTrue(calls.get(1).script().contains("issueId"));
        assertEquals(calls.get(0).args()[1], calls.get(1).args()[0]);
        assertEquals(calls.get(0).keys().get(0), calls.get(1).keys().get(0));
    }

    @ParameterizedTest
    @CsvSource({"0,COMMIT", "1,ROLLBACK", "2,COMMIT"})
    void transactionCompletionUsesSameOwner(int status, String action) {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        service.reserveInTransaction(VerificationSceneEnum.EMAIL_REGISTER, "user@example.com", "123456");
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCompletion(status);
        assertEquals(calls.get(0).args()[1], calls.get(1).args()[0]);
        assertEquals(action, calls.get(1).args()[1]);
    }

    @Test
    void requiresTransactionAndRejectsWrongCodeWithoutCallback() {
        assertThrows(IllegalStateException.class, () -> service.reserveInTransaction(
                VerificationSceneEnum.EMAIL_REGISTER, "user@example.com", "123456"));
        assertTrue(calls.isEmpty());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        result = -3;
        assertEquals(400, assertThrows(BizException.class, () -> service.reserveInTransaction(
                VerificationSceneEnum.EMAIL_REGISTER, "user@example.com", "123456")).getCode());
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }
}
