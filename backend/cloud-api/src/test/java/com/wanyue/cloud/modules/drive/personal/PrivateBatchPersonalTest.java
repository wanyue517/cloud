package com.wanyue.cloud.modules.drive.personal;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import com.wanyue.cloud.modules.drive.DriveModels;
import com.wanyue.cloud.modules.drive.DriveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PrivateBatchPersonalTest {
    @MockitoBean com.wanyue.cloud.common.config.RedisStartupCheck redisStartupCheck;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired JwtTokenProvider jwt;
    @Autowired DriveService drive;
    @Autowired PasswordEncoder passwordEncoder;

    long alice;
    long personalSpace;
    String token;

    @BeforeEach
    void setup() {
        alice = IdWorker.getId();
        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?,'Alice',1)", alice);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)", IdWorker.getId(), alice);
        db.update("INSERT INTO auth_password_credential(id,user_id,password_hash) VALUES (?,?,?)",
                IdWorker.getId(), alice, passwordEncoder.encode("Vault#2026"));
        personalSpace = Long.parseLong(drive.createSpace(alice, new DriveModels.SpaceInput("常规空间", "", "#2563eb", 0L)));
        token = jwt.createAccessToken(alice, 0);
    }

    @Test
    void privateSpaceEnforcesUnlockBlocksSearchAndLocksImmediately() throws Exception {
        String privateSpaceId = drive.createSpace(alice, new DriveModels.SpaceInput("私密保险箱", "", "#ef4444", 1L, "", "PRIVATE"));

        // Locked by default -> 423
        mvc.perform(get("/api/v1/spaces/" + privateSpaceId + "/nodes").header("Authorization", "Bearer " + token))
                .andExpect(status().isLocked());

        // Wrong password -> 403
        mvc.perform(post("/api/v1/spaces/" + privateSpaceId + "/private/unlock")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"password\":\"bad\",\"idleMinutes\":10}"))
                .andExpect(status().isForbidden());

        // Right password -> returns short-lived token
        String unlockJson = mvc.perform(post("/api/v1/spaces/" + privateSpaceId + "/private/unlock")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"password\":\"Vault#2026\",\"idleMinutes\":10}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String pToken = new tools.jackson.databind.json.JsonMapper().readTree(unlockJson).get("data").get("privateToken").asString();

        // With private token -> upload and read succeed
        var secret = new MockMultipartFile("file", "vault-key.txt", "text/plain", "VaultSecretToken999".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/spaces/" + privateSpaceId + "/files").file(secret)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Private-Token", pToken))
                .andExpect(status().isOk());

        // Global search MUST NEVER leak private space files
        mvc.perform(get("/api/v1/search").param("query", "vault-key").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));

        // Lock -> subsequent request returns 423 again
        mvc.perform(post("/api/v1/spaces/" + privateSpaceId + "/private/lock").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/spaces/" + privateSpaceId + "/nodes")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Private-Token", pToken))
                .andExpect(status().isLocked());
    }

    @Test
    void vaultUnlockTogglesSpacePrivacyAndManagesPrivateItems() throws Exception {
        var json = new tools.jackson.databind.json.JsonMapper();
        // 未解锁时私密设置不可访问
        mvc.perform(get("/api/v1/me/private/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isLocked());
        mvc.perform(post("/api/v1/me/private/unlock").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"password\":\"bad\",\"idleMinutes\":10}"))
                .andExpect(status().isForbidden());
        String unlock = mvc.perform(post("/api/v1/me/private/unlock").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"password\":\"Vault#2026\",\"idleMinutes\":10}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String vault = json.readTree(unlock).get("data").get("privateToken").asString();

        // 单独设置私密文件：主列表与搜索均不可见，私密设置中可见
        var f = new MockMultipartFile("file", "diary-secret.txt", "text/plain", "DiaryBody".getBytes(StandardCharsets.UTF_8));
        String up = mvc.perform(multipart("/api/v1/spaces/" + personalSpace + "/files").file(f).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String fileId = json.readTree(up).get("data").get("id").asString();
        mvc.perform(put("/api/v1/spaces/" + personalSpace + "/nodes/" + fileId + "/hidden").param("enabled", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/spaces/" + personalSpace + "/nodes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/v1/me/private/overview").header("Authorization", "Bearer " + token).header("X-Private-Token", vault))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(fileId))
                .andExpect(jsonPath("$.data.spaces[0].privateEnabled").value(false));

        // 取消私密必须持有私密设置授权
        mvc.perform(put("/api/v1/spaces/" + personalSpace + "/nodes/" + fileId + "/hidden").param("enabled", "false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isLocked());

        // 单独开启空间私密：无授权访问返回 423，持全局授权可访问
        mvc.perform(put("/api/v1/me/private/spaces/" + personalSpace).param("enabled", "true")
                        .header("Authorization", "Bearer " + token).header("X-Private-Token", vault))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/spaces/" + personalSpace + "/nodes").header("Authorization", "Bearer " + token))
                .andExpect(status().isLocked());
        mvc.perform(get("/api/v1/spaces/" + personalSpace + "/nodes").header("Authorization", "Bearer " + token).header("X-Private-Token", vault))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/spaces/" + personalSpace + "/nodes/" + fileId + "/hidden").param("enabled", "false")
                        .header("Authorization", "Bearer " + token).header("X-Private-Token", vault))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/me/private/spaces/" + personalSpace).param("enabled", "false")
                        .header("Authorization", "Bearer " + token).header("X-Private-Token", vault))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/spaces/" + personalSpace + "/nodes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));

        // 一键锁定后授权失效
        mvc.perform(post("/api/v1/me/private/lock").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/private/overview").header("Authorization", "Bearer " + token).header("X-Private-Token", vault))
                .andExpect(status().isLocked());
    }

    @Test
    void hiddenFilesBatchZipPreferencesAndRecentAccessWork() throws Exception {
        var f1 = new MockMultipartFile("file", "a.txt", "text/plain", "AAA".getBytes(StandardCharsets.UTF_8));
        var f2 = new MockMultipartFile("file", "b.txt", "text/plain", "BBB".getBytes(StandardCharsets.UTF_8));
        String j1 = mvc.perform(multipart("/api/v1/spaces/" + personalSpace + "/files").file(f1).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String j2 = mvc.perform(multipart("/api/v1/spaces/" + personalSpace + "/files").file(f2).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id1 = new tools.jackson.databind.json.JsonMapper().readTree(j1).get("data").get("id").asString();
        String id2 = new tools.jackson.databind.json.JsonMapper().readTree(j2).get("data").get("id").asString();

        // Record recent access & verify list
        mvc.perform(post("/api/v1/spaces/" + personalSpace + "/nodes/" + id1 + "/access").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/recent").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(id1));

        // Hide f1 -> cannot share or pin, and disappears from recent
        mvc.perform(put("/api/v1/spaces/" + personalSpace + "/nodes/" + id1 + "/hidden").param("enabled", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/spaces/" + personalSpace + "/nodes/" + id1 + "/pin").param("enabled", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/recent").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // Batch ZIP download for f2
        String zipJson = mvc.perform(post("/api/v1/spaces/" + personalSpace + "/batch/zip")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"nodeIds\":[\"" + id2 + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();
        String jobId = new tools.jackson.databind.json.JsonMapper().readTree(zipJson).get("data").get("id").asString();
        mvc.perform(get("/api/v1/batch/jobs/" + jobId + "/download").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // User preferences persist
        mvc.perform(put("/api/v1/me/preferences").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"theme\":\"dark\",\"density\":\"compact\",\"fileView\":\"grid\",\"fileSort\":\"updated\",\"defaultSpaceId\":\"" + personalSpace + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileView").value("grid"))
                .andExpect(jsonPath("$.data.defaultSpaceId").value(String.valueOf(personalSpace)));
    }
}
