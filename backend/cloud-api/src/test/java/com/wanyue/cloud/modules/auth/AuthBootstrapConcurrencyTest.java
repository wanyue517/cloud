package com.wanyue.cloud.modules.auth;

import com.wanyue.cloud.common.config.RedisStartupCheck;
import com.wanyue.cloud.modules.auth.service.RegistrationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * T019 真实并发验证：多线程同时争抢首个超级管理员名额，有且仅有一个线程成功获得 SUPER_ADMIN。
 */
@SpringBootTest
class AuthBootstrapConcurrencyTest {

    @MockitoBean
    RedisStartupCheck redisStartupCheck;

    @Autowired
    JdbcTemplate db;

    @Autowired
    RegistrationPolicy policy;

    @Autowired
    TransactionTemplate tx;

    @Test
    void concurrentBootstrapClaimsGrantSuperAdminToExactlyOneCaller() throws Exception {
        Boolean previous = db.queryForObject("SELECT admin_claimed FROM system_bootstrap WHERE id=1", Boolean.class);
        db.update("UPDATE system_bootstrap SET admin_claimed=FALSE WHERE id=1");
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return tx.execute(status -> policy.claimRole());
                }));
            }
            ready.await();
            go.countDown();
            long admins = 0;
            long users = 0;
            for (Future<String> f : futures) {
                String role = f.get();
                if ("SUPER_ADMIN".equals(role)) admins++;
                else if ("USER".equals(role)) users++;
            }
            assertEquals(1L, admins, "并发注册必须有且仅有一个初始化超级管理员");
            assertEquals(threads - 1L, users);
        } finally {
            pool.shutdownNow();
            db.update("UPDATE system_bootstrap SET admin_claimed=? WHERE id=1", Boolean.TRUE.equals(previous));
        }
    }
}
