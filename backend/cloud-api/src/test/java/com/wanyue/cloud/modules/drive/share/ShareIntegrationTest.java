package com.wanyue.cloud.modules.drive.share;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShareIntegrationTest {
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
        db.update("INSERT INTO auth_identity(id,user_id,identity_type,identifier,is_primary) VALUES (?,?,'EMAIL',?,1)",
                IdWorker.getId(), alice, "alice-" + alice + "@test.local");

        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?,'Bob',1)", bob);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)", IdWorker.getId(), bob);
        db.update("INSERT INTO auth_identity(id,user_id,identity_type,identifier,is_primary) VALUES (?,?,'EMAIL',?,1)",
                IdWorker.getId(), bob, "bob-" + bob + "@test.local");

        aliceSpace = Long.parseLong(drive.createSpace(alice, new DriveModels.SpaceInput("Alice工作区", "", "#2563eb", 0L)));
        aliceToken = jwt.createAccessToken(alice, 0);
        bobToken = jwt.createAccessToken(bob, 0);
    }

    @Test
    void sharedSpaceLifecycleEnforcesRolesAndRevocationImmediately() throws Exception {
        // Upload file before converting to shared space
        var file = new MockMultipartFile("file", "spec.txt", "text/plain", "preserved".getBytes(StandardCharsets.UTF_8));
        String upJson = mvc.perform(multipart("/api/v1/spaces/" + aliceSpace + "/files").file(file).header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String fileId = new tools.jackson.databind.json.JsonMapper().readTree(upJson).get("data").get("id").asString();

        // Convert to shared space
        mvc.perform(post("/api/v1/spaces/" + aliceSpace + "/convert-shared").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk());

        // Invite Bob as VIEWER by email
        String invJson = mvc.perform(post("/api/v1/spaces/" + aliceSpace + "/invitations")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType("application/json")
                        .content("{\"target\":\"bob-" + bob + "@test.local\",\"role\":\"VIEWER\",\"message\":\"欢迎加入\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String invId = new tools.jackson.databind.json.JsonMapper().readTree(invJson).get("data").asString();

        // Before accepting, Bob cannot access the space
        mvc.perform(get("/api/v1/spaces/" + aliceSpace + "/nodes").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNotFound());

        // Bob sees pending invitation and accepts it
        mvc.perform(get("/api/v1/shares/invitations").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(invId));
        mvc.perform(post("/api/v1/shares/invitations/" + invId + "/accept").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk());

        // Bob sees the shared space in his mixed space list with role VIEWER
        mvc.perform(get("/api/v1/spaces").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(String.valueOf(aliceSpace)))
                .andExpect(jsonPath("$.data[0].kind").value("SHARED"))
                .andExpect(jsonPath("$.data[0].role").value("VIEWER"));

        // Bob can read preserved file but cannot create folder as VIEWER
        mvc.perform(get("/api/v1/spaces/" + aliceSpace + "/nodes/" + fileId + "/content").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(content().string("preserved"));
        mvc.perform(post("/api/v1/spaces/" + aliceSpace + "/folders")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType("application/json")
                        .content("{\"parentId\":0,\"name\":\"Denied\"}"))
                .andExpect(status().isForbidden());

        // Upgrade Bob to EDITOR -> can create folder
        mvc.perform(put("/api/v1/spaces/" + aliceSpace + "/members/" + bob)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType("application/json")
                        .content("{\"role\":\"EDITOR\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/spaces/" + aliceSpace + "/folders")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType("application/json")
                        .content("{\"parentId\":0,\"name\":\"CollabDir\"}"))
                .andExpect(status().isOk());

        // Remove Bob -> access is revoked immediately
        mvc.perform(delete("/api/v1/spaces/" + aliceSpace + "/members/" + bob).header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/spaces/" + aliceSpace + "/nodes/" + fileId + "/content").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void folderShareInheritsSubtreeTruncatesAncestorsAndBlocksParentOrSiblingGuessing() throws Exception {
        // Tree: /SecretParent/SharedRoot/ChildFolder/doc.txt and /OtherSibling/private.txt
        var secretParent = drive.folder(alice, aliceSpace, new DriveModels.NodeInput("SecretParent", 0L));
        var sharedRoot = drive.folder(alice, aliceSpace, new DriveModels.NodeInput("SharedRoot", Long.parseLong(secretParent.id())));
        var childFolder = drive.folder(alice, aliceSpace, new DriveModels.NodeInput("ChildFolder", Long.parseLong(sharedRoot.id())));
        var otherSibling = drive.folder(alice, aliceSpace, new DriveModels.NodeInput("OtherSibling", 0L));

        var docFile = new MockMultipartFile("file", "doc.txt", "text/plain", "shared-doc".getBytes(StandardCharsets.UTF_8));
        String docJson = mvc.perform(multipart("/api/v1/spaces/" + aliceSpace + "/files")
                        .file(docFile).param("parentId", childFolder.id()).header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String docId = new tools.jackson.databind.json.JsonMapper().readTree(docJson).get("data").get("id").asString();

        var privFile = new MockMultipartFile("file", "private.txt", "text/plain", "top-secret".getBytes(StandardCharsets.UTF_8));
        String privJson = mvc.perform(multipart("/api/v1/spaces/" + aliceSpace + "/files")
                        .file(privFile).param("parentId", otherSibling.id()).header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String privId = new tools.jackson.databind.json.JsonMapper().readTree(privJson).get("data").get("id").asString();

        // Share SharedRoot with Bob as VIEWER
        String shareJson = mvc.perform(post("/api/v1/spaces/" + aliceSpace + "/nodes/" + sharedRoot.id() + "/shares")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType("application/json")
                        .content("{\"target\":\"" + bob + "\",\"permission\":\"VIEWER\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String shareId = new tools.jackson.databind.json.JsonMapper().readTree(shareJson).get("data").asString();

        // Bob sees SharedRoot in shared-with-me
        mvc.perform(get("/api/v1/shares/with-me").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nodeId").value(sharedRoot.id()))
                .andExpect(jsonPath("$.data[0].ownerName").value("Alice"));

        // Bob can list ChildFolder and read doc.txt
        mvc.perform(get("/api/v1/spaces/" + aliceSpace + "/nodes").param("parentId", childFolder.id()).header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(docId));
        mvc.perform(get("/api/v1/spaces/" + aliceSpace + "/nodes/" + docId + "/content").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(content().string("shared-doc"));

        // Ancestors of ChildFolder for Bob MUST truncate at SharedRoot (never expose SecretParent)
        mvc.perform(get("/api/v1/spaces/" + aliceSpace + "/nodes/" + childFolder.id() + "/ancestors").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(sharedRoot.id()))
                .andExpect(jsonPath("$.data[1].id").value(childFolder.id()));

        // Bob CANNOT guess SecretParent, space root, or OtherSibling/private.txt
        mvc.perform(get("/api/v1/spaces/" + aliceSpace + "/nodes").param("parentId", secretParent.id()).header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/spaces/" + aliceSpace + "/nodes").param("parentId", "0").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/spaces/" + aliceSpace + "/nodes/" + privId + "/content").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNotFound());

        // Revoke share -> Bob loses access immediately
        mvc.perform(delete("/api/v1/shares/resources/" + shareId).header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/spaces/" + aliceSpace + "/nodes/" + docId + "/content").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNotFound());
    }
}
