package com.wanyue.cloud.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/** Redis 是验证码与限流的必需依赖，连接或认证失败时终止应用初始化。 */
@Component
public class RedisStartupCheck implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(RedisStartupCheck.class);

    private final RedisConnectionFactory connectionFactory;

    public RedisStartupCheck(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void afterPropertiesSet() {
        // 单例初始化阶段执行，失败会取消上下文刷新，不等到第一个业务请求才发现故障。
        try (RedisConnection connection = connectionFactory.getConnection()) {
            if (!"PONG".equalsIgnoreCase(connection.ping())) {
                throw new IllegalStateException("Redis PING 未返回 PONG");
            }
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Redis 启动检查失败，应用终止启动。请检查 Redis 服务、连接地址、端口和认证配置。", e);
        }
        log.info("Redis 启动检查通过（PING/PONG）");
    }
}
