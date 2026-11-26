package com.wanyue.cloud.modules.auth.service.impl;

import com.wanyue.cloud.modules.auth.enums.VerificationChannelEnum;
import com.wanyue.cloud.modules.auth.enums.VerificationSceneEnum;
import com.wanyue.cloud.modules.auth.service.VerificationCodeService;
import com.wanyue.cloud.modules.auth.sender.VerificationCodeSender;
import com.wanyue.cloud.modules.auth.config.VerificationProperties;
import com.wanyue.cloud.modules.auth.service.VerificationRateLimiter;
import com.wanyue.cloud.modules.auth.support.EmailNormalizer;
import com.wanyue.cloud.common.exception.BizException;
import org.springframework.http.HttpStatus;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK;

@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {
    @Override
    public void invalidate(VerificationSceneEnum scene, String target, String issueId) {
        if (issueId == null || issueId.isBlank()) {
            throw new IllegalArgumentException("签发标识不能为空");
        }
        stringRedisTemplate.execute(CANCEL_SCRIPT,
                List.of(codeKey(scene, normalize(scene, target))), issueId);
    }

    private static final Logger log =
            LoggerFactory.getLogger(VerificationCodeServiceImpl.class);
    private static final RedisScript<Long> ISSUE_SCRIPT =
            script("redis/verification-issue.lua");

    private static final RedisScript<Long> RESERVE_SCRIPT =
            script("redis/verification-reserve.lua");

    private static final RedisScript<Long> FINISH_SCRIPT =
            script("redis/verification-finish.lua");

    private static final RedisScript<Long> CANCEL_SCRIPT =
            script("redis/verification-cancel.lua");

    private final SecureRandom secureRandom = new SecureRandom();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private List<VerificationCodeSender> verificationCodeSenders;

    @Resource
    private VerificationProperties properties;

    @Resource
    private VerificationRateLimiter rateLimiter;

    /**
     * 发送验证码
     */
    @Override
    public void send(VerificationSceneEnum scene, String target) {
        String normalized = normalize(scene, target);

        VerificationCodeSender sender = verificationCodeSenders.stream()
                .filter(s -> s.channel() == scene.getChannel())
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("验证码发送渠道未配置")
                );

        rateLimiter.checkTarget(hmac("TARGET|" + scene.getChannel() + "|" + normalized));

        String code = String.format(
                Locale.ROOT,
                "%06d",
                secureRandom.nextInt(1_000_000)
        );

        String issueId = UUID.randomUUID().toString();
        String key = codeKey(scene, normalized);

        Long result = stringRedisTemplate.execute(
                ISSUE_SCRIPT,
                List.of(key, cooldownKey(scene, normalized)),
                codeHash(scene, normalized, code),
                issueId,
                String.valueOf(properties.getCodeTtlSeconds()),
                String.valueOf(properties.getSendIntervalSeconds())
        );

        if (!Long.valueOf(1L).equals(result)) {
            throw new BizException(HttpStatus.TOO_MANY_REQUESTS, 429, "验证码发送过于频繁，请稍后重试");
        }

        try {
            sender.send(scene, normalized, code, properties.getCodeTtlSeconds());
        } catch (RuntimeException e) {
            // 只撤销本次签发的验证码
            try {
                invalidate(scene, normalized, issueId);
            } catch (Exception redisException) {
                log.error("验证码发送失败后清理 Redis 状态异常", redisException);
            }

            log.error("验证码邮件投递失败，scene={}", scene, e);
            throw new BizException(HttpStatus.SERVICE_UNAVAILABLE, 503, "验证码发送失败，请稍后重试");
        }
    }

    /**
     * 原子校验并占用验证码
     *
     * 必须在 Spring 数据库事务中调用。
     * 提交后消费，回滚后恢复。
     */
    @Override
    public void reserveInTransaction(
            VerificationSceneEnum scene,
            String target,
            String code) {

        if (!TransactionSynchronizationManager
                .isActualTransactionActive()
                || !TransactionSynchronizationManager
                .isSynchronizationActive()) {

            throw new IllegalStateException(
                    "验证码验证必须在业务事务内执行"
            );
        }

        String normalized = normalize(scene, target);
        String key = codeKey(scene, normalized);
        String owner = UUID.randomUUID().toString();

        Long result = stringRedisTemplate.execute(
                RESERVE_SCRIPT,
                List.of(key),
                codeHash(scene, normalized, code),
                owner,
                String.valueOf(properties.getMaxFailures())
        );

        if (!Long.valueOf(1L).equals(result)) {
            throw new BizException(HttpStatus.BAD_REQUEST, 400, "验证码错误、已过期或暂不可用");
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCompletion(int status) {
                        String action =
                                status == STATUS_ROLLED_BACK
                                        ? "ROLLBACK"
                                        : "COMMIT";

                        // 事务结果未知时不恢复验证码，避免重新使用
                        try {
                            stringRedisTemplate.execute(
                                    FINISH_SCRIPT,
                                    List.of(key),
                                    owner,
                                    action
                            );
                        } catch (Exception e) {
                            log.error("验证码事务完成后处理失败", e);
                        }
                    }
                }
        );
    }

    /**
     * 邮箱统一小写；手机号按 E.164 规范传入。
     */
    private String normalize(VerificationSceneEnum scene, String target) {
        if (scene == null || target == null || target.isBlank()) {
            throw new BizException(HttpStatus.BAD_REQUEST, 400, "验证码场景和接收目标不能为空");
        }
        return scene.getChannel() == VerificationChannelEnum.EMAIL
                ? EmailNormalizer.normalize(target)
                : target.trim();
    }

    /**
     * 同一目标和场景使用一致的 Redis Cluster Hash Tag。
     */
    private String keyPrefix(VerificationSceneEnum scene, String target) {
        String digest = hmac(
                "KEY|" + scene.name() + "|" + target
        );

        return "cloud:verify:{" + digest + "}";
    }

    private String codeKey(VerificationSceneEnum scene, String target) {
        return keyPrefix(scene, target) + ":code";
    }

    private String cooldownKey(VerificationSceneEnum scene, String target) {
        return keyPrefix(scene, target) + ":cooldown";
    }

    private String codeHash(
            VerificationSceneEnum scene,
            String target,
            String code) {

        return hmac(
                "OTP|" + scene.name() + "|" + target + "|" + code
        );
    }

    private String hmac(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");

            mac.init(new SecretKeySpec(
                    properties.getHmacSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));

            return HexFormat.of().formatHex(
                    mac.doFinal(input.getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    private static RedisScript<Long> script(String location) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();

        script.setLocation(new ClassPathResource(location));
        script.setResultType(Long.class);

        return script;
    }
}
