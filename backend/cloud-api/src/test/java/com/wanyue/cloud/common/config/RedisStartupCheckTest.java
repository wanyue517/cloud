package com.wanyue.cloud.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisStartupCheckTest {
    private final RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
    private final RedisConnection connection = mock(RedisConnection.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(RedisConnectionFactory.class, () -> factory)
            .withBean(RedisStartupCheck.class);

    @Test
    void reachableRedisAllowsStartupAndReleasesConnection() {
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertNotNull(context.getBean(RedisStartupCheck.class));
        });
        verify(connection).ping();
        verify(connection).close();
    }

    @Test
    void unavailableRedisFailsApplicationInitialization() {
        when(factory.getConnection()).thenThrow(new RedisConnectionFailureException("connection refused"));
        assertStartupFailure();
        verifyNoInteractions(connection);
    }

    @Test
    void pingFailureAlsoStopsStartupAndReleasesConnection() {
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenThrow(new RedisConnectionFailureException("timeout or authentication failure"));
        assertStartupFailure();
        verify(connection).close();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "unexpected"})
    void invalidPingResponseDoesNotAllowStartup(String response) {
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn(response);
        assertStartupFailure();
        verify(connection).close();
    }

    private void assertStartupFailure() {
        runner.run(context -> {
            Throwable failure = context.getStartupFailure();
            assertNotNull(failure);
            IllegalStateException cause = assertInstanceOf(IllegalStateException.class, failure.getCause());
            assertTrue(cause.getMessage().contains("Redis 启动检查失败"));
            assertNotNull(cause.getCause());
        });
    }
}
