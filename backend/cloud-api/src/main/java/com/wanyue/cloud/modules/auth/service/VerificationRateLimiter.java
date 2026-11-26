package com.wanyue.cloud.modules.auth.service;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.auth.config.VerificationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** 限制请求尝试次数，失败请求也计数，避免故障时反复冲击 SMTP。 */
@Service
public class VerificationRateLimiter {
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>();
    static {
        SCRIPT.setLocation(new ClassPathResource("redis/verification-rate-limit.lua"));
        SCRIPT.setResultType(Long.class);
    }
    private final StringRedisTemplate redis;
    private final VerificationProperties properties;

    public VerificationRateLimiter(StringRedisTemplate redis, VerificationProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public void checkIp(String ip) {
        // 使用直连地址，不信任客户端自行提交的 X-Forwarded-For。
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(ip.getBytes(StandardCharsets.UTF_8)));
            check("ip:" + digest, properties.getIpHourlyLimit(), 3600);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public void checkTarget(String targetDigest) {
        check("target:" + targetDigest, properties.getTargetDailyLimit(), 86400);
        check("global", properties.getGlobalHourlyLimit(), 3600);
    }

    private void check(String scope, int limit, int seconds) {
        Long result = redis.execute(SCRIPT, List.of("cloud:verify:rate:" + scope),
                String.valueOf(limit), String.valueOf(seconds));
        if (!Long.valueOf(1).equals(result)) {
            throw new BizException(HttpStatus.TOO_MANY_REQUESTS, 429, "验证码请求过于频繁，请稍后重试");
        }
    }
}
