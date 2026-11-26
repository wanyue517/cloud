package com.wanyue.cloud.modules.drive;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import com.wanyue.cloud.modules.auth.service.WebSessionService;
import com.wanyue.cloud.modules.auth.service.RegistrationPolicy;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DriveHttpTest {
    @MockitoBean com.wanyue.cloud.common.config.RedisStartupCheck redisStartupCheck;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired JwtTokenProvider jwt;
    @Autowired DriveService drive;
    @Autowired WebSessionService sessions;
    @Autowired RegistrationPolicy policy;
    long user,space;
    String token;
    @BeforeEach void setup() {
        user=IdWorker.getId();
        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?,'HTTP test',1)",user);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)",IdWorker.getId(),user);
        space=Long.parseLong(drive.createSpace(user,new DriveModels.SpaceInput("HTTP空间","","#112233",0L)));
        token=jwt.createAccessToken(user,0);
    }
    @Test void bearerIsRequiredAndStringIdsAndRangeWork() throws Exception {
        mvc.perform(get("/api/v1/spaces")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/spaces").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(String.valueOf(space)));
        var file=new MockMultipartFile("file","range.txt","text/plain","abcdef".getBytes());
        String response=mvc.perform(multipart("/api/v1/spaces/"+space+"/files").file(file).header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").isString()).andReturn().getResponse().getContentAsString();
        var tree=new tools.jackson.databind.json.JsonMapper().readTree(response);
        String id=tree.get("data").get("id").asString();
        mvc.perform(get("/api/v1/spaces/"+space+"/nodes/"+id+"/content").header("Authorization","Bearer "+token).header("Range","bytes=1-3"))
                .andExpect(status().isPartialContent()).andExpect(content().string("bcd")).andExpect(header().string("Content-Range","bytes 1-3/6"));
        mvc.perform(get("/api/v1/spaces/"+space+"/nodes/"+id+"/content").header("Authorization","Bearer "+token).header("Range","bytes=100-200"))
                .andExpect(status().isRequestedRangeNotSatisfiable());
    }
    @Test void revokedAndDisabledAccountsAndOrdinaryAdminAccessAreRejected() throws Exception {
        mvc.perform(get("/api/admin/space/space/page").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        db.update("UPDATE sys_user SET status=0 WHERE id=?",user);
        mvc.perform(get("/api/v1/spaces").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        db.update("UPDATE sys_user SET status=1 WHERE id=?",user);
        sessions.logout(user);
        mvc.perform(get("/api/v1/spaces").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
    @Test void refreshRotatesAndOldRefreshCannotBeReused() {
        String refresh=sessions.start(user);
        var next=sessions.refresh(refresh);
        assertNotEquals(refresh,next.refreshToken());assertEquals(String.valueOf(user),jwt.verify(next.accessToken()).getSubject());
        assertThrows(com.wanyue.cloud.common.exception.BizException.class,()->sessions.refresh(refresh));
        sessions.logout(user);
        assertThrows(com.wanyue.cloud.common.exception.BizException.class,()->sessions.refresh(next.refreshToken()));
    }
    @Test void bootstrapClaimIsSingleUseWithinTransaction() {
        db.update("UPDATE system_bootstrap SET admin_claimed=FALSE WHERE id=1");
        assertEquals("SUPER_ADMIN",policy.claimRole());assertEquals("USER",policy.claimRole());
    }
    @Test void adminApiRequiresRoleAndCannotDisableCurrentAdmin() throws Exception {
        mvc.perform(get("/api/v1/admin/stats").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        db.update("INSERT INTO auth_user_role(id,user_id,role_id) VALUES (?,?,1)",IdWorker.getId(),user);
        mvc.perform(get("/api/v1/admin/stats").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.users").isNumber());
        mvc.perform(put("/api/v1/admin/users/"+user+"/status").header("Authorization","Bearer "+token).contentType("application/json").content("{\"status\":0}"))
                .andExpect(status().isConflict());
    }
    @Test void profileUpdateIsScopedToCurrentAccount() throws Exception {
        mvc.perform(put("/api/v1/me").header("Authorization","Bearer "+token).contentType("application/json")
                        .content("{\"displayName\":\"New name\",\"avatarUrl\":\"https://cdn.example.com/a.png\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me").header("Authorization","Bearer "+token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("New name"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://cdn.example.com/a.png"))
                .andExpect(jsonPath("$.data.id").isString());
        mvc.perform(put("/api/v1/me").header("Authorization","Bearer "+token).contentType("application/json")
                        .content("{\"displayName\":\"New name\",\"avatarUrl\":\"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/me").header("Authorization","Bearer "+token).contentType("application/json").content("{\"displayName\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void deviceSessionsListRevokeAndBlockCrossUserIdor() throws Exception {
        String first = sessions.start(user, "Mozilla/5.0 (Windows NT 10.0) Chrome/130.0", "127.0.0.1");
        String second = sessions.start(user, "Mozilla/5.0 (Macintosh; Intel Mac OS X) Safari/18.0", "127.0.0.2");
        String body = mvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + token)
                        .cookie(new jakarta.servlet.http.Cookie("cloudlane.refresh", first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        var items = new tools.jackson.databind.json.JsonMapper().readTree(body).get("data");
        String secondId = null;
        int currentCount = 0;
        for (var item : items) {
            if (item.get("current").asBoolean()) currentCount++;
            else secondId = item.get("id").asString();
        }
        assertEquals(1, currentCount);
        assertNotNull(secondId);

        long otherUser = IdWorker.getId();
        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?,'Other user',1)", otherUser);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)", IdWorker.getId(), otherUser);
        String otherToken = jwt.createAccessToken(otherUser, 0);
        mvc.perform(delete("/api/v1/auth/sessions/" + secondId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/v1/auth/sessions/" + secondId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThrows(com.wanyue.cloud.common.exception.BizException.class, () -> sessions.refresh(second));
        assertNotNull(sessions.refresh(first).accessToken());
    }
    @Test void adminStorageProfilesAndTasksRequireSuperAdminAndWork() throws Exception {
        mvc.perform(get("/api/v1/admin/storage-profiles").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/tasks").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        db.update("INSERT INTO auth_user_role(id,user_id,role_id) VALUES (?,?,1)", IdWorker.getId(), user);

        mvc.perform(get("/api/v1/admin/storage-profiles").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").isString())
                .andExpect(jsonPath("$.data[0].provider").value("LOCAL"));

        mvc.perform(post("/api/v1/admin/storage-profiles").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"name\":\"非法路径\",\"provider\":\"LOCAL\",\"rootPath\":\"../escape\"}"))
                .andExpect(status().isBadRequest());

        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("cloud-storage-profile-test-");
        try {
            String escapedPath = tempDir.toAbsolutePath().toString().replace("\\", "\\\\");
            String created = mvc.perform(post("/api/v1/admin/storage-profiles").header("Authorization", "Bearer " + token)
                            .contentType("application/json")
                            .content("{\"name\":\"测试本地存储\",\"provider\":\"LOCAL\",\"rootPath\":\"" + escapedPath + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isString())
                    .andReturn().getResponse().getContentAsString();
            String profileId = new tools.jackson.databind.json.JsonMapper().readTree(created).get("data").asString();

            mvc.perform(post("/api/v1/admin/storage-profiles/" + profileId + "/test").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.ok").value(true));

            mvc.perform(put("/api/v1/admin/storage-profiles/" + profileId + "/default").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            mvc.perform(get("/api/v1/admin/tasks").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.summary.UPLOADING").isNumber())
                    .andExpect(jsonPath("$.data.items").isArray());

            mvc.perform(post("/api/v1/admin/tasks/cleanup-expired").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.cleaned").isNumber());
        } finally {
            java.nio.file.Files.deleteIfExists(tempDir);
        }
    }
    @Test void userCanListOwnUploadSessions() throws Exception {
        String created = mvc.perform(post("/api/v1/uploads").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"spaceId\":\"" + space + "\",\"parentId\":\"0\",\"name\":\"chunked.bin\",\"size\":1024,\"mimeType\":\"application/octet-stream\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isString())
                .andReturn().getResponse().getContentAsString();
        String uploadId = new tools.jackson.databind.json.JsonMapper().readTree(created).get("data").get("id").asString();

        mvc.perform(get("/api/v1/uploads").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(uploadId))
                .andExpect(jsonPath("$.data[0].name").value("chunked.bin"))
                .andExpect(jsonPath("$.data[0].status").value("UPLOADING"))
                .andExpect(jsonPath("$.data[0].receivedCount").value(0));
    }
    @Test void previewContentAndTextRangeEnforceOwnershipAndBounds() throws Exception {
        String longText = "第一行中文日志内容\n".repeat(120);
        var file = new MockMultipartFile("file", "app.log", "text/plain", longText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String uploaded = mvc.perform(multipart("/api/v1/spaces/" + space + "/files").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String fileId = new tools.jackson.databind.json.JsonMapper().readTree(uploaded).get("data").get("id").asString();

        // Owner reads text in chunks without corrupting UTF-8 boundaries
        String chunk1Json = mvc.perform(get("/api/v1/spaces/" + space + "/nodes/" + fileId + "/text")
                        .param("offset", "0").param("limit", "1024")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.binaryDetected").value(false))
                .andReturn().getResponse().getContentAsString();
        var chunk1 = new tools.jackson.databind.json.JsonMapper().readTree(chunk1Json).get("data");
        int bytesRead1 = chunk1.get("bytesRead").asInt();
        String part1 = chunk1.get("content").asString();
        assertTrue(bytesRead1 > 0 && bytesRead1 <= 1024);

        String chunk2Json = mvc.perform(get("/api/v1/spaces/" + space + "/nodes/" + fileId + "/text")
                        .param("offset", String.valueOf(bytesRead1)).param("limit", "65536")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andReturn().getResponse().getContentAsString();
        String part2 = new tools.jackson.databind.json.JsonMapper().readTree(chunk2Json).get("data").get("content").asString();
        assertEquals(longText, part1 + part2);

        // Security headers on preview content
        mvc.perform(get("/api/v1/spaces/" + space + "/nodes/" + fileId + "/content").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox; default-src 'none'"));

        // Unauthenticated and other users are blocked from content and text endpoints
        mvc.perform(get("/api/v1/spaces/" + space + "/nodes/" + fileId + "/text"))
                .andExpect(status().isUnauthorized());

        long stranger = IdWorker.getId();
        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?,'Stranger',1)", stranger);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)", IdWorker.getId(), stranger);
        String strangerToken = jwt.createAccessToken(stranger, 0);

        mvc.perform(get("/api/v1/spaces/" + space + "/nodes/" + fileId + "/content").header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/spaces/" + space + "/nodes/" + fileId + "/text").header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isNotFound());
    }
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Test void verifyPasswordChecksCurrentUserCredential() throws Exception {
        db.update("INSERT INTO auth_password_credential(id,user_id,password_hash) VALUES (?,?,?)",
                IdWorker.getId(), user, passwordEncoder.encode("Secret#2026"));
        mvc.perform(post("/api/v1/me/verify-password").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"password\":\"WrongPass\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/me/verify-password").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"password\":\"Secret#2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }
}
