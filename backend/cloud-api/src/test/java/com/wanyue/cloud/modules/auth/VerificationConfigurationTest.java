package com.wanyue.cloud.modules.auth;

import com.wanyue.cloud.modules.auth.config.VerificationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class VerificationConfigurationTest {
    @Configuration
    @EnableConfigurationProperties(VerificationProperties.class)
    static class Config {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues("MAIL_USERNAME=test@example.com", "MAIL_PASSWORD=test-only",
                    "VERIFICATION_HMAC_SECRET=test-only-secret-at-least-32-characters")
            .withInitializer(context -> {
                try {
                    new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"))
                            .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });

    @Test
    void productionYamlBindsRootProperties() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            VerificationProperties properties = context.getBean(VerificationProperties.class);
            assertEquals(300, properties.getCodeTtlSeconds());
            // 本地配置允许覆盖环境变量占位符；不要在断言失败时打印真实凭据。
            assertTrue(properties.getHmacSecret().length() >= 32);
            assertTrue(context.getEnvironment().containsProperty("cloud.verification.email.from"));
            assertTrue(context.getEnvironment().containsProperty("spring.mail.password"));
            assertNull(context.getEnvironment().getProperty("spring.cloud.verification.hmac-secret"));
        });
    }

    @Test
    void weakSecretAndInvalidLimitsPreventStartup() {
        runner.withPropertyValues("cloud.verification.hmac-secret=short").run(context ->
                assertNotNull(context.getStartupFailure()));
        runner.withPropertyValues("cloud.verification.max-failures=0").run(context ->
                assertNotNull(context.getStartupFailure()));
    }
}
