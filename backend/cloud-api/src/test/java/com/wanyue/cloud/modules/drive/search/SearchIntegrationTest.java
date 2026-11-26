package com.wanyue.cloud.modules.drive.search;

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
class SearchIntegrationTest {
    @MockitoBean com.wanyue.cloud.common.config.RedisStartupCheck redisStartupCheck;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired JwtTokenProvider jwt;
    @Autowired DriveService drive;
    @Autowired SearchIndexService indexService;

    long alice, bob;
    long spaceA, spaceB;
    String aliceToken, bobToken;

    @BeforeEach
    void setup() {
        alice = IdWorker.getId();
        bob = IdWorker.getId();
        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?,'Alice',1)", alice);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)", IdWorker.getId(), alice);
        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?,'Bob',1)", bob);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)", IdWorker.getId(), bob);
        spaceA = Long.parseLong(drive.createSpace(alice, new DriveModels.SpaceInput("研发空间", "", "#2563eb", 0L)));
        spaceB = Long.parseLong(drive.createSpace(alice, new DriveModels.SpaceInput("财务空间", "", "#10b981", 1L)));
        aliceToken = jwt.createAccessToken(alice, 0);
        bobToken = jwt.createAccessToken(bob, 0);
    }

    @Test
    void outboxIndexesLongContentChunksSupportsFilteredSearchAndBlocksUnauthorizedUsers() throws Exception {
        String longBody = "常规文档段落。\n".repeat(300) + "核心架构关键词：CloudlaneDistributedClosure2026\n" + "结尾说明。\n".repeat(100);
        var mdFile = new MockMultipartFile("file", "architecture.md", "text/plain", longBody.getBytes(StandardCharsets.UTF_8));
        String upJson = mvc.perform(multipart("/api/v1/spaces/" + spaceA + "/files").file(mdFile).header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var node = new tools.jackson.databind.json.JsonMapper().readTree(upJson).get("data");
        String fileId = node.get("id").asString();

        var csvFile = new MockMultipartFile("file", "q1-budget.csv", "text/plain", "dept,amount\neng,1000".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/v1/spaces/" + spaceB + "/files").file(csvFile).header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk());

        // Consume outbox events synchronously
        int processed = indexService.processPendingOutbox(20);
        assertTrue(processed >= 2);

        // Long text is split into multiple chunks with file/version reference
        Integer chunkCount = db.queryForObject("SELECT COUNT(*) FROM search_text_chunk WHERE node_id=?", Integer.class, Long.parseLong(fileId));
        assertNotNull(chunkCount);
        assertTrue(chunkCount >= 2);

        // Full-text body search matches architecture.md with snippet and extension filter
        mvc.perform(get("/api/v1/search")
                        .param("query", "CloudlaneDistributedClosure2026")
                        .param("extension", "md")
                        .param("spaceId", String.valueOf(spaceA))
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(fileId))
                .andExpect(jsonPath("$.data.items[0].matchSource").value("CONTENT"))
                .andExpect(jsonPath("$.data.items[0].snippet").isNotEmpty());

        // Space filter excludes spaceA when searching spaceB
        mvc.perform(get("/api/v1/search")
                        .param("query", "CloudlaneDistributedClosure2026")
                        .param("spaceId", String.valueOf(spaceB))
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));

        // Unauthorized user Bob gets zero results
        mvc.perform(get("/api/v1/search")
                        .param("query", "CloudlaneDistributedClosure2026")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));

        // Deleting file enqueues DELETE outbox and cleans chunks
        mvc.perform(delete("/api/v1/spaces/" + spaceA + "/nodes/" + fileId)
                        .param("revision", node.get("revision").asString())
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk());
        indexService.processPendingOutbox(20);
        Integer remaining = db.queryForObject("SELECT COUNT(*) FROM search_text_chunk WHERE node_id=?", Integer.class, Long.parseLong(fileId));
        assertEquals(0, remaining);
    }
}
