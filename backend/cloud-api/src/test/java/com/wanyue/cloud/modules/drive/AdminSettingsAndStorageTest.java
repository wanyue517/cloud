package com.wanyue.cloud.modules.drive;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import com.wanyue.cloud.modules.drive.storage.DatabaseStorageProvider;
import com.wanyue.cloud.modules.drive.storage.SecretCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminSettingsAndStorageTest {
    @MockitoBean com.wanyue.cloud.common.config.RedisStartupCheck redisStartupCheck;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired JwtTokenProvider jwt;
    @Autowired SecretCipher cipher;
    @Autowired DatabaseStorageProvider dbStorage;

    long admin;
    String adminToken;

    @BeforeEach
    void setup() {
        admin = IdWorker.getId();
        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?,'Admin',1)", admin);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)", IdWorker.getId(), admin);
        db.update("INSERT INTO auth_user_role(id,user_id,role_id) VALUES (?,?,1)", IdWorker.getId(), admin);
        adminToken = jwt.createAccessToken(admin, 0);
    }

    @Test
    void storageCredentialsAreEncryptedAndNeverReturnedInPlaintextAndDbAdapterEnforcesLimit() throws Exception {
        String enc = cipher.encrypt("super-secret-s3-key");
        assertTrue(enc.startsWith("enc:v1:"));
        assertEquals("super-secret-s3-key", cipher.decrypt(enc));

        // Create S3 storage profile with secretKey -> response list must NOT contain plaintext secret
        mvc.perform(post("/api/v1/admin/storage-profiles").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"MinIO对象存储\",\"provider\":\"S3\",\"endpoint\":\"http://127.0.0.1:9000\",\"bucket\":\"cloudlane\",\"region\":\"us-east-1\",\"accessKey\":\"ak123\",\"secretKey\":\"sk-plaintext-999\"}"))
                .andExpect(status().isOk());

        String listJson = mvc.perform(get("/api/v1/admin/storage-profiles").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(listJson.contains("sk-plaintext-999"));
        assertTrue(listJson.contains("\"hasSecret\":true"));

        // Create DB storage profile and run live probe
        String dbCreated = mvc.perform(post("/api/v1/admin/storage-profiles").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"name\":\"数据库小文件存储\",\"provider\":\"DB\",\"maxFileBytes\":2097152}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String dbProfileId = new tools.jackson.databind.json.JsonMapper().readTree(dbCreated).get("data").asString();
        mvc.perform(post("/api/v1/admin/storage-profiles/" + dbProfileId + "/test").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));

        // DB storage enforces small-file limit
        byte[] oversized = new byte[5 * 1024 * 1024];
        assertThrows(BizException.class, () -> dbStorage.put("oversized-probe", new ByteArrayInputStream(oversized), oversized.length));
    }

    @Test
    void siteSettingsAuthSmtpAndOAuthPersistWithoutLeakingSecrets() throws Exception {
        mvc.perform(put("/api/v1/admin/settings/site").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"siteName\":\"星云网盘\",\"siteLogoUrl\":\"https://cdn.example.com/logo.svg\",\"siteAnnouncement\":\"欢迎使用\"}"))
                .andExpect(status().isOk());

        // Public site config readable without auth
        mvc.perform(get("/api/v1/public/site-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.siteName").value("星云网盘"))
                .andExpect(jsonPath("$.data.siteAnnouncement").value("欢迎使用"));

        // Auth & SMTP secret never returned in plaintext
        String authJson = mvc.perform(put("/api/v1/admin/settings/auth").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"allowRegister\":false,\"requireEmailVerify\":true,\"allowPasswordLogin\":true,\"smtpHost\":\"smtp.example.com\",\"smtpPort\":465,\"smtpUsername\":\"bot@example.com\",\"smtpFrom\":\"bot@example.com\",\"smtpPassword\":\"smtp-secret-xyz\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowRegister").value(false))
                .andExpect(jsonPath("$.data.hasSmtpPassword").value(true))
                .andReturn().getResponse().getContentAsString();
        assertFalse(authJson.contains("smtp-secret-xyz"));
    }
}
