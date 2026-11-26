package com.wanyue.cloud.modules.drive;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in browser integration: the runner creates and drops a disposable schema.
 * No API, authentication, database or Redis mocks are used.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "CLOUD_T008_E2E", matches = "true")
class FrontendSessionE2ETest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry properties) {
        String url = System.getenv("T008_DB_URL");
        if (url == null || !url.matches("jdbc:mysql://[^/]+/cloud_t008_e2e_[a-f0-9]{12}\\?.*"))
            throw new IllegalStateException("Use scripts/verify-t008-live.py with a disposable schema");
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> System.getenv("T008_DB_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("T008_DB_PASSWORD"));
        properties.add("cloud.auth.jwt.secret", () -> System.getenv("T008_JWT_SECRET"));
        properties.add("cloud.verification.hmac-secret", () -> System.getenv("T008_VERIFICATION_SECRET"));
    }

    private long account(String email, long role) {
        long user = IdWorker.getId();
        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?, ?, 1)", user, email);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?, ?)", IdWorker.getId(), user);
        db.update("INSERT INTO auth_identity(id,user_id,identity_type,identifier,is_primary,verified_at) VALUES (?,?,'EMAIL',?,1,UTC_TIMESTAMP(3))",
                IdWorker.getId(), user, email);
        db.update("INSERT INTO auth_password_credential(id,user_id,password_hash) VALUES (?,?,?)",
                IdWorker.getId(), user, passwords.encode(System.getenv("T008_LOGIN_PASSWORD")));
        db.update("INSERT INTO auth_user_role(id,user_id,role_id) VALUES (?,?,?)", IdWorker.getId(), user, role);
        return user;
    }

    @Test @Timeout(180)
    void browserUsesRealSameOriginSessionAcrossApps() throws Exception {
        assertTrue(db.queryForObject("SELECT DATABASE()", String.class).startsWith("cloud_t008_e2e_"));
        long admin = account("admin-t008@example.test", 1);
        long member = account("member-t008@example.test", 2);
        Path script = Path.of("../../frontend/apps/web/tests/workspace-live-browser.py").toAbsolutePath().normalize();
        if (!java.nio.file.Files.exists(script)) {
            script = Path.of("../frontend/apps/web/tests/workspace-live-browser.py").toAbsolutePath().normalize();
        }
        var runner = new ProcessBuilder(System.getenv().getOrDefault("T008_PYTHON", "python"),
                script.toString());
        runner.environment().put("T008_BACKEND_PORT", String.valueOf(port));
        runner.environment().put("T008_ADMIN_ID", String.valueOf(admin));
        runner.environment().put("T008_MEMBER_ID", String.valueOf(member));
        runner.redirectErrorStream(true);
        Process process = runner.start();
        try {
            assertTrue(process.waitFor(150, TimeUnit.SECONDS), "Browser integration timed out");
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            System.out.print(output);
            assertEquals(0, process.exitValue(), "Browser integration failed: " + output);
            assertEquals(2L, db.queryForObject("SELECT COUNT(*) FROM sys_user", Long.class));
            assertTrue(db.queryForObject("SELECT COUNT(*) FROM auth_session WHERE revoked_at IS NOT NULL AND user_id=?", Long.class, admin) > 0);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
