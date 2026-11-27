package com.wanyue.cloud.modules.drive.sharelink;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShareLinkAndMessageTest {
    @MockitoBean com.wanyue.cloud.common.config.RedisStartupCheck redisStartupCheck;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired JwtTokenProvider jwt;
    @Autowired DriveService drive;

    long alice, bob;
    long aliceSpace;
    String aliceToken, bobToken;

    @BeforeEach
    void setup() {
        alice = IdWorker.getId();
        bob = IdWorker.getId();
        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?,'Alice',1)", alice);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)", IdWorker.getId(), alice);
        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?,'Bob',1)", bob);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)", IdWorker.getId(), bob);
        aliceSpace = Long.parseLong(drive.createSpace(alice, new DriveModels.SpaceInput("分享测试空间", "", "#10b981", 0L)));
        aliceToken = jwt.createAccessToken(alice, 0);
        bobToken = jwt.createAccessToken(bob, 0);
    }

    @Test
    void shareLinkStoresOnlyDigestEnforcesPasswordBoundsAndDisable() throws Exception {
        var folder = drive.folder(alice, aliceSpace, new DriveModels.NodeInput("PublicBox", 0L));
        var other = drive.folder(alice, aliceSpace, new DriveModels.NodeInput("PrivateBox", 0L));

        var pubFile = new MockMultipartFile("file", "guide.txt", "text/plain", "public-content".getBytes(StandardCharsets.UTF_8));
        String pubJson = mvc.perform(multipart("/api/v1/spaces/" + aliceSpace + "/files")
                        .file(pubFile).param("parentId", folder.id()).header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String pubFileId = new tools.jackson.databind.json.JsonMapper().readTree(pubJson).get("data").get("id").asString();

        var privFile = new MockMultipartFile("file", "secret.txt", "text/plain", "secret".getBytes(StandardCharsets.UTF_8));
        String privJson = mvc.perform(multipart("/api/v1/spaces/" + aliceSpace + "/files")
                        .file(privFile).param("parentId", other.id()).header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String privFileId = new tools.jackson.databind.json.JsonMapper().readTree(privJson).get("data").get("id").asString();

        String created = mvc.perform(post("/api/v1/spaces/" + aliceSpace + "/nodes/" + folder.id() + "/share-links")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType("application/json")
                        .content("{\"password\":\"8899\",\"expireDays\":7,\"allowDownload\":true,\"maxDownloads\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasPassword").value(true))
                .andReturn().getResponse().getContentAsString();
        var data = new tools.jackson.databind.json.JsonMapper().readTree(created).get("data");
        String linkId = data.get("id").asString();
        String rawToken = data.get("token").asString();
        assertTrue(rawToken.length() >= 32);

        // Database stores SHA-256 digest only, never raw token
        String storedHash = db.queryForObject("SELECT token_hash FROM share_link WHERE id=?", String.class, Long.parseLong(linkId));
        assertNotEquals(rawToken, storedHash);
        assertEquals(ShareLinkService.sha256Hex(rawToken), storedHash);

        // Public meta works without login and reports requiresPassword=true
        mvc.perform(get("/api/v1/public/shares/" + rawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requiresPassword").value(true))
                .andExpect(jsonPath("$.data.unlocked").value(false));

        // Wrong password rejected with 403
        mvc.perform(post("/api/v1/public/shares/" + rawToken + "/verify")
                        .contentType("application/json").content("{\"password\":\"0000\"}"))
                .andExpect(status().isForbidden());

        // Right password lists folder and downloads file inside folder
        mvc.perform(get("/api/v1/public/shares/" + rawToken + "/nodes").header("X-Share-Password", "8899"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(pubFileId));
        mvc.perform(get("/api/v1/public/shares/" + rawToken + "/content")
                        .header("X-Share-Password", "8899").param("nodeId", pubFileId))
                .andExpect(status().isOk())
                .andExpect(content().string("public-content"));

        // Cannot escape shared folder to access PrivateBox/secret.txt
        mvc.perform(get("/api/v1/public/shares/" + rawToken + "/content")
                        .header("X-Share-Password", "8899").param("nodeId", privFileId))
                .andExpect(status().isNotFound());

        // Disable link -> immediately returns 404
        mvc.perform(post("/api/v1/share-links/" + linkId + "/disable").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/shares/" + rawToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shareInvitationAndAdminBroadcastDeliverMessagesWithReadTracking() throws Exception {
        // Ordinary user cannot publish system message
        mvc.perform(post("/api/v1/admin/messages")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType("application/json")
                        .content("{\"scope\":\"ALL\",\"title\":\"非法公告\",\"content\":\"x\"}"))
                .andExpect(status().isForbidden());

        // Grant Alice SUPER_ADMIN and publish broadcast
        db.update("INSERT INTO auth_user_role(id,user_id,role_id) VALUES (?,?,1)", IdWorker.getId(), alice);
        mvc.perform(post("/api/v1/admin/messages")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType("application/json")
                        .content("{\"scope\":\"ALL\",\"title\":\"系统维护通知\",\"content\":\"今晚不停机升级\",\"actionUrl\":\"/app\"}"))
                .andExpect(status().isOk());

        // Bob sees unread system message
        String inboxJson = mvc.perform(get("/api/v1/messages").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("系统维护通知"))
                .andReturn().getResponse().getContentAsString();
        String msgId = new tools.jackson.databind.json.JsonMapper().readTree(inboxJson).get("data").get("items").get(0).get("id").asString();

        // Mark read -> unreadCount becomes 0
        mvc.perform(put("/api/v1/messages/" + msgId + "/read").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/messages").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    void publicShareSaveEnforcesAuthBoundsAndCopiesSubtree() throws Exception {
        long bobSpace = Long.parseLong(drive.createSpace(bob, new DriveModels.SpaceInput("Bob网盘", "", "#2563eb", 0L)));
        var sharedDir = drive.folder(alice, aliceSpace, new DriveModels.NodeInput("SharedKit", 0L));
        var outsideDir = drive.folder(alice, aliceSpace, new DriveModels.NodeInput("OutsideDir", 0L));

        var fileA = new MockMultipartFile("file", "notes.txt", "text/plain", "hello-save".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/spaces/" + aliceSpace + "/files")
                        .file(fileA).param("parentId", sharedDir.id()).header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk());

        var outsideFile = new MockMultipartFile("file", "secret.txt", "text/plain", "nope".getBytes(StandardCharsets.UTF_8));
        String outsideJson = mvc.perform(multipart("/api/v1/spaces/" + aliceSpace + "/files")
                        .file(outsideFile).param("parentId", outsideDir.id()).header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String outsideFileId = new tools.jackson.databind.json.JsonMapper().readTree(outsideJson).get("data").get("id").asString();

        String created = mvc.perform(post("/api/v1/spaces/" + aliceSpace + "/nodes/" + sharedDir.id() + "/share-links")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType("application/json")
                        .content("{\"password\":\"4321\",\"expireDays\":7,\"allowDownload\":true}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String rawToken = new tools.jackson.databind.json.JsonMapper().readTree(created).get("data").get("token").asString();

        // 1. Unauthenticated save -> 401
        mvc.perform(post("/api/v1/public/shares/" + rawToken + "/save")
                        .contentType("application/json")
                        .content("{\"targetSpaceId\":" + bobSpace + ",\"targetParentId\":0,\"password\":\"4321\"}"))
                .andExpect(status().isUnauthorized());

        // 2. Wrong extract code -> 403
        mvc.perform(post("/api/v1/public/shares/" + rawToken + "/save")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType("application/json")
                        .content("{\"targetSpaceId\":" + bobSpace + ",\"targetParentId\":0,\"password\":\"0000\"}"))
                .andExpect(status().isForbidden());

        // 3. Out-of-scope nodeId -> 404
        mvc.perform(post("/api/v1/public/shares/" + rawToken + "/save")
                        .header("Authorization", "Bearer " + bobToken)
                        .header("X-Share-Password", "4321")
                        .contentType("application/json")
                        .content("{\"targetSpaceId\":" + bobSpace + ",\"targetParentId\":0,\"nodeId\":" + outsideFileId + "}"))
                .andExpect(status().isNotFound());

        // 4. Valid save deep-copies shared folder + child file into Bob's space
        String savedJson = mvc.perform(post("/api/v1/public/shares/" + rawToken + "/save")
                        .header("Authorization", "Bearer " + bobToken)
                        .header("X-Share-Password", "4321")
                        .contentType("application/json")
                        .content("{\"targetSpaceId\":" + bobSpace + ",\"targetParentId\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("SharedKit"))
                .andExpect(jsonPath("$.data.nodeType").value("FOLDER"))
                .andReturn().getResponse().getContentAsString();
        String copiedDirId = new tools.jackson.databind.json.JsonMapper().readTree(savedJson).get("data").get("id").asString();

        // Verify child file was deep-copied inside Bob's copied directory
        mvc.perform(get("/api/v1/spaces/" + bobSpace + "/nodes")
                        .param("parentId", copiedDirId)
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("notes.txt"));
    }
}
