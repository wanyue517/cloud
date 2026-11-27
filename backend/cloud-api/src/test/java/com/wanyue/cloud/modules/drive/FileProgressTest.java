package com.wanyue.cloud.modules.drive;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FileProgressTest {
    @MockitoBean com.wanyue.cloud.common.config.RedisStartupCheck redisStartupCheck;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired JwtTokenProvider jwt;

    long ownerId;
    long strangerId;
    String ownerToken;
    String strangerToken;
    long spaceId;
    long nodeId;

    @BeforeEach
    void setup() throws Exception {
        ownerId = IdWorker.getId();
        strangerId = IdWorker.getId();
        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?,'Owner',1)", ownerId);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)", IdWorker.getId(), ownerId);
        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?,'Stranger',1)", strangerId);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)", IdWorker.getId(), strangerId);
        ownerToken = jwt.createAccessToken(ownerId, 0);
        strangerToken = jwt.createAccessToken(strangerId, 0);

        // Create a personal space for owner
        String spaceJson = mvc.perform(post("/api/v1/spaces")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ProgressSpace\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        spaceId = Long.parseLong(new tools.jackson.databind.json.JsonMapper().readTree(spaceJson).get("data").asString());

        // Upload a sample file
        MockMultipartFile file = new MockMultipartFile("file", "demo.pdf", "application/pdf", "%PDF-1.4\n".getBytes());
        String uploadJson = mvc.perform(multipart("/api/v1/spaces/" + spaceId + "/files")
                        .file(file)
                        .param("parentId", "0")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        nodeId = Long.parseLong(new tools.jackson.databind.json.JsonMapper().readTree(uploadJson).get("data").get("id").asString());
    }

    @Test
    void progressCrudAndStrictPermissionIsolation() throws Exception {
        // 1. Initially null
        mvc.perform(get("/api/v1/spaces/" + spaceId + "/nodes/" + nodeId + "/progress")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        // 2. Save PDF page progress
        mvc.perform(put("/api/v1/spaces/" + spaceId + "/nodes/" + nodeId + "/progress")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"progressType\":\"PDF\",\"positionValue\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progressType").value("PDF"))
                .andExpect(jsonPath("$.data.positionValue").value(12.0));

        // 3. Upsert updates position
        mvc.perform(put("/api/v1/spaces/" + spaceId + "/nodes/" + nodeId + "/progress")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"progressType\":\"PDF\",\"positionValue\":27}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.positionValue").value(27.0));

        mvc.perform(get("/api/v1/spaces/" + spaceId + "/nodes/" + nodeId + "/progress")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.positionValue").value(27.0));

        // 4. Stranger cannot GET or PUT -> 404
        mvc.perform(get("/api/v1/spaces/" + spaceId + "/nodes/" + nodeId + "/progress")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/v1/spaces/" + spaceId + "/nodes/" + nodeId + "/progress")
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"progressType\":\"VIDEO\",\"positionValue\":99}"))
                .andExpect(status().isNotFound());

        // 5. Validation rejects unknown progressType or negative position
        mvc.perform(put("/api/v1/spaces/" + spaceId + "/nodes/" + nodeId + "/progress")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"progressType\":\"UNKNOWN\",\"positionValue\":5}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/v1/spaces/" + spaceId + "/nodes/" + nodeId + "/progress")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"progressType\":\"VIDEO\",\"positionValue\":-3}"))
                .andExpect(status().isBadRequest());
    }
}
