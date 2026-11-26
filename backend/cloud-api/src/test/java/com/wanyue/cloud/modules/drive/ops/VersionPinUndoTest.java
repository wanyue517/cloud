package com.wanyue.cloud.modules.drive.ops;

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
class VersionPinUndoTest {
    @MockitoBean com.wanyue.cloud.common.config.RedisStartupCheck redisStartupCheck;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired JwtTokenProvider jwt;
    @Autowired DriveService drive;

    long alice;
    long space;
    String token;

    @BeforeEach
    void setup() {
        alice = IdWorker.getId();
        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?,'Alice',1)", alice);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)", IdWorker.getId(), alice);
        space = Long.parseLong(drive.createSpace(alice, new DriveModels.SpaceInput("阶段10空间", "", "#6366f1", 0L)));
        token = jwt.createAccessToken(alice, 0);
    }

    @Test
    void versionLimitsEnforcedByAdminAndUserAndPrunedOnReplace() throws Exception {
        db.update("INSERT INTO auth_user_role(id,user_id,role_id) VALUES (?,?,1)", IdWorker.getId(), alice);

        // Set system max versions to 3
        mvc.perform(put("/api/v1/admin/settings/files").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"maxFileVersions\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxFileVersions").value(3));

        // User cannot set personal limit higher than system limit (3)
        mvc.perform(put("/api/v1/me/version-policy").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"maxVersions\":5}"))
                .andExpect(status().isBadRequest());

        // User sets personal limit to 2
        mvc.perform(put("/api/v1/me/version-policy").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{\"maxVersions\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxVersions").value(2));

        // Upload v1, replace to v2, replace to v3 -> pruned to 2 latest versions
        var f1 = new MockMultipartFile("file", "note.txt", "text/plain", "v1".getBytes(StandardCharsets.UTF_8));
        String r1 = mvc.perform(multipart("/api/v1/spaces/" + space + "/files").file(f1).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var n1 = new tools.jackson.databind.json.JsonMapper().readTree(r1).get("data");
        String fileId = n1.get("id").asString();

        var f2 = new MockMultipartFile("file", "note.txt", "text/plain", "v2".getBytes(StandardCharsets.UTF_8));
        String r2 = mvc.perform(multipart("/api/v1/spaces/" + space + "/files").file(f2)
                        .param("replaceId", fileId).param("revision", n1.get("revision").asString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var n2 = new tools.jackson.databind.json.JsonMapper().readTree(r2).get("data");

        var f3 = new MockMultipartFile("file", "note.txt", "text/plain", "v3".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/spaces/" + space + "/files").file(f3)
                        .param("replaceId", fileId).param("revision", n2.get("revision").asString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/spaces/" + space + "/nodes/" + fileId + "/versions").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].number").value(3))
                .andExpect(jsonPath("$.data[0].createdByName").value("Alice"));
    }

    @Test
    void quickAccessPrunesHiddenAndDeletedNodesAutomatically() throws Exception {
        var folder = drive.folder(alice, space, new DriveModels.NodeInput("PinnedDir", 0L));
        mvc.perform(put("/api/v1/spaces/" + space + "/nodes/" + folder.id() + "/pin").param("enabled", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/quick-access").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        // Hide the folder -> quick-access automatically removes stale pin
        db.update("UPDATE file_node SET hidden=TRUE WHERE id=?", Long.parseLong(folder.id()));
        mvc.perform(get("/api/v1/quick-access").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void batchRenamePreviewApplyAndTenSecondUndoWork() throws Exception {
        var f1 = drive.folder(alice, space, new DriveModels.NodeInput("Alpha", 0L));
        var f2 = drive.folder(alice, space, new DriveModels.NodeInput("Beta", 0L));

        // Preview prefix rename
        mvc.perform(post("/api/v1/spaces/" + space + "/nodes/batch-rename/preview")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"nodeIds\":[\"" + f1.id() + "\",\"" + f2.id() + "\"],\"mode\":\"PREFIX\",\"prefix\":\"2026_\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].newName").value("2026_Alpha"))
                .andExpect(jsonPath("$.data[1].newName").value("2026_Beta"))
                .andExpect(jsonPath("$.data[0].conflict").value(false));

        // Apply batch rename and get 10-second undo token
        String applyJson = mvc.perform(post("/api/v1/spaces/" + space + "/nodes/batch-rename/apply")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"nodeIds\":[\"" + f1.id() + "\",\"" + f2.id() + "\"],\"mode\":\"PREFIX\",\"prefix\":\"2026_\",\"conflictPolicy\":\"ABORT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.renamedCount").value(2))
                .andReturn().getResponse().getContentAsString();
        String undoToken = new tools.jackson.databind.json.JsonMapper().readTree(applyJson).get("data").get("undo").get("tokenId").asString();

        // Execute undo -> names restored to Alpha / Beta
        mvc.perform(post("/api/v1/undo/" + undoToken).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/spaces/" + space + "/nodes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("Alpha"))
                .andExpect(jsonPath("$.data.items[1].name").value("Beta"));

        // Second undo with same token rejected with 409
        mvc.perform(post("/api/v1/undo/" + undoToken).header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }
}
