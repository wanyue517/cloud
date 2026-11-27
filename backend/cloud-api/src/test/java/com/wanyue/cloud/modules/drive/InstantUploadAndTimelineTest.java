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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InstantUploadAndTimelineTest {
    @MockitoBean com.wanyue.cloud.common.config.RedisStartupCheck redisStartupCheck;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired JwtTokenProvider jwt;

    long aliceId;
    long bobId;
    String aliceToken;
    String bobToken;
    long aliceSpaceId;
    long bobSpaceId;

    @BeforeEach
    void setup() throws Exception {
        aliceId = IdWorker.getId();
        bobId = IdWorker.getId();
        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?,'Alice',1)", aliceId);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)", IdWorker.getId(), aliceId);
        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?,'Bob',1)", bobId);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)", IdWorker.getId(), bobId);
        aliceToken = jwt.createAccessToken(aliceId, 0);
        bobToken = jwt.createAccessToken(bobId, 0);

        aliceSpaceId = createSpace(aliceToken, "AliceSpace", "PERSONAL");
        bobSpaceId = createSpace(bobToken, "BobSpace", "PERSONAL");
    }

    private long createSpace(String token, String name, String kind) throws Exception {
        String json = mvc.perform(post("/api/v1/spaces")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"kind\":\"" + kind + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(new tools.jackson.databind.json.JsonMapper().readTree(json).get("data").asString());
    }

    @Test
    void sha256InstantUploadIsStrictlyUserScoped() throws Exception {
        byte[] content = "Cloudlane M039 instant dedup payload".getBytes(StandardCharsets.UTF_8);
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));

        // 1. Alice uploads original file
        MockMultipartFile file = new MockMultipartFile("file", "origin.txt", "text/plain", content);
        mvc.perform(multipart("/api/v1/spaces/" + aliceSpaceId + "/files")
                        .file(file)
                        .param("parentId", "0")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk());

        // 2. Alice initializes chunked upload with same SHA-256 -> instantHit = true, COMPLETED immediately!
        mvc.perform(post("/api/v1/uploads")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spaceId\":" + aliceSpaceId + ",\"parentId\":0,\"name\":\"copy-instant.txt\",\"size\":"
                                + content.length + ",\"mimeType\":\"text/plain\",\"expectedSha256\":\"" + sha256 + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.instantHit").value(true))
                .andExpect(jsonPath("$.data.fileId").isNotEmpty());

        // 3. Bob initializes chunked upload with same SHA-256 -> MUST NOT hit instant upload!
        mvc.perform(post("/api/v1/uploads")
                        .header("Authorization", "Bearer " + bobToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spaceId\":" + bobSpaceId + ",\"parentId\":0,\"name\":\"probe.txt\",\"size\":"
                                + content.length + ",\"mimeType\":\"text/plain\",\"expectedSha256\":\"" + sha256 + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UPLOADING"))
                .andExpect(jsonPath("$.data.instantHit").value(false))
                .andExpect(jsonPath("$.data.fileId").doesNotExist());
    }

    @Test
    void mediaTimelineExcludesPrivateHiddenNonMediaAndOtherUsers() throws Exception {
        byte[] dummyJpg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
        byte[] dummyMp4 = "0000ftypisom".getBytes(StandardCharsets.UTF_8);

        // Upload visible photo, video, non-media txt, and hidden photo to Alice's normal space
        mvc.perform(multipart("/api/v1/spaces/" + aliceSpaceId + "/files")
                        .file(new MockMultipartFile("file", "vacation.jpg", "image/jpeg", dummyJpg))
                        .param("parentId", "0")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk());

        mvc.perform(multipart("/api/v1/spaces/" + aliceSpaceId + "/files")
                        .file(new MockMultipartFile("file", "clip.mp4", "video/mp4", dummyMp4))
                        .param("parentId", "0")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk());

        mvc.perform(multipart("/api/v1/spaces/" + aliceSpaceId + "/files")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes()))
                        .param("parentId", "0")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk());

        String hiddenUpload = mvc.perform(multipart("/api/v1/spaces/" + aliceSpaceId + "/files")
                        .file(new MockMultipartFile("file", "secret.jpg", "image/jpeg", dummyJpg))
                        .param("parentId", "0")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long hiddenId = Long.parseLong(new tools.jackson.databind.json.JsonMapper().readTree(hiddenUpload).get("data").get("id").asString());
        mvc.perform(put("/api/v1/spaces/" + aliceSpaceId + "/nodes/" + hiddenId + "/hidden")
                        .param("enabled", "true")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk());

        // Bob uploads a photo to his own space
        mvc.perform(multipart("/api/v1/spaces/" + bobSpaceId + "/files")
                        .file(new MockMultipartFile("file", "bob-photo.jpg", "image/jpeg", dummyJpg))
                        .param("parentId", "0")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk());

        // Alice timeline must contain only vacation.jpg and clip.mp4 (total = 2)
        mvc.perform(get("/api/v1/media/timeline")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[*].name", containsInAnyOrder("vacation.jpg", "clip.mp4")))
                .andExpect(jsonPath("$.data.items[*].name", not(hasItem("notes.txt"))))
                .andExpect(jsonPath("$.data.items[*].name", not(hasItem("secret.jpg"))))
                .andExpect(jsonPath("$.data.items[*].name", not(hasItem("bob-photo.jpg"))));

        // IMAGE filter returns only vacation.jpg
        mvc.perform(get("/api/v1/media/timeline")
                        .param("mediaType", "IMAGE")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("vacation.jpg"));
    }
}
