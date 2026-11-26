package com.wanyue.cloud.modules.ops;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import com.wanyue.cloud.modules.drive.preview.ArchivePreviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 阶段 14 工程质量、安全与交付综合集成测试（T165/T166/T167/T168/T169/T171/T172/T173）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityAndDeliveryTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired JwtTokenProvider jwt;
    @Autowired AsyncJobWorkerService jobWorker;
    @Autowired StorageGcService storageGc;
    @Autowired ArchivePreviewService archivePreview;
    @MockitoBean com.wanyue.cloud.common.config.RedisStartupCheck redisStartupCheck;

    long adminId;
    long userAId;
    long userBId;
    String adminToken;
    String userAToken;
    String userBToken;

    @BeforeEach
    void setup() {
        adminId = IdWorker.getId();
        userAId = IdWorker.getId();
        userBId = IdWorker.getId();
        db.update("INSERT INTO sys_user(id, display_name, status, deleted) VALUES (?, '运维超管', 1, 0)", adminId);
        db.update("INSERT INTO auth_user_security(id, user_id) VALUES (?, ?)", IdWorker.getId(), adminId);
        db.update("INSERT INTO auth_user_role(id, user_id, role_id) VALUES (?, ?, 1)", IdWorker.getId(), adminId);

        db.update("INSERT INTO sys_user(id, display_name, status, deleted) VALUES (?, '用户A', 1, 0)", userAId);
        db.update("INSERT INTO auth_user_security(id, user_id) VALUES (?, ?)", IdWorker.getId(), userAId);

        db.update("INSERT INTO sys_user(id, display_name, status, deleted) VALUES (?, '用户B', 1, 0)", userBId);
        db.update("INSERT INTO auth_user_security(id, user_id) VALUES (?, ?)", IdWorker.getId(), userBId);

        adminToken = jwt.createAccessToken(adminId, 0);
        userAToken = jwt.createAccessToken(userAId, 0);
        userBToken = jwt.createAccessToken(userBId, 0);
    }

    @Test
    void workerRetryDeadLetterAndManualRecoveryFlow() throws Exception {
        long jobId = jobWorker.enqueue("preview-group", "OFFICE_CONVERT", "{\"nodeId\":\"1\"}", 2);
        // 第一次失败 -> PENDING
        String s1 = jobWorker.executeImmediate(jobId, j -> { throw new RuntimeException("transient error 1"); });
        assertEquals("PENDING", s1);
        // 第二次失败达到 maxRetries=2 -> DEAD_LETTER
        String s2 = jobWorker.executeImmediate(jobId, j -> { throw new RuntimeException("transient error 2"); });
        assertEquals("DEAD_LETTER", s2);

        // 管理后台能看到死信任务并支持手动重试
        mvc.perform(get("/api/v1/admin/jobs?status=DEAD_LETTER").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.DEAD_LETTER").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mvc.perform(post("/api/v1/admin/jobs/" + jobId + "/retry").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 重试后成功执行
        String s3 = jobWorker.executeImmediate(jobId, j -> {});
        assertEquals("SUCCEEDED", s3);
    }

    @Test
    void storageGcReclaimsOnlyOrphansAndNeverDeletesReferencedBlobs() throws Exception {
        long orphanBlobId = IdWorker.getId();
        long referencedBlobId = IdWorker.getId();
        db.update("INSERT INTO blob_object(id, storage_profile_id, storage_key, size_bytes, sha256, created_at) "
                        + "VALUES (?, 1, ?, 128, ?, TIMESTAMPADD(HOUR, -3, UTC_TIMESTAMP(3)))",
                orphanBlobId, "gc-test-orphan-" + orphanBlobId, "a".repeat(64));
        db.update("INSERT INTO blob_object(id, storage_profile_id, storage_key, size_bytes, sha256, created_at) "
                        + "VALUES (?, 1, ?, 256, ?, TIMESTAMPADD(HOUR, -3, UTC_TIMESTAMP(3)))",
                referencedBlobId, "gc-test-ref-" + referencedBlobId, "b".repeat(64));

        // 为 referencedBlobId 建立有效空间、节点与版本引用
        long spaceId = IdWorker.getId();
        long nodeId = IdWorker.getId();
        long verId = IdWorker.getId();
        db.update("INSERT INTO space(id,owner_user_id,kind,name,description,icon,color,sort_order,created_by) VALUES (?,?,'PERSONAL','GC测试空间','','','#6366f1',0,?)",
                spaceId, userAId, userAId);
        db.update("INSERT INTO file_node(id,space_id,parent_id,node_type,name,created_by) VALUES (?,?,0,'FILE','keep.txt',?)",
                nodeId, spaceId, userAId);
        db.update("INSERT INTO file_version(id,file_id,blob_id,version_number,mime_type,created_by) VALUES (?,?,?,1,'text/plain',?)",
                verId, nodeId, referencedBlobId, userAId);

        // Dry run 不删除任何记录
        var dry = storageGc.scanAndCollect(true, 60);
        assertTrue(((Number) dry.get("orphanCount")).intValue() >= 1);
        assertEquals(0, ((Number) dry.get("deletedCount")).intValue());

        // 实际回收仅删孤儿，绝不误删仍有引用的 referencedBlobId
        mvc.perform(post("/api/v1/admin/storage/gc?dryRun=false&graceMinutes=60").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        Integer orphanLeft = db.queryForObject("SELECT COUNT(*) FROM blob_object WHERE id=?", Integer.class, orphanBlobId);
        Integer refLeft = db.queryForObject("SELECT COUNT(*) FROM blob_object WHERE id=?", Integer.class, referencedBlobId);
        assertEquals(0, orphanLeft);
        assertEquals(1, refLeft);
    }

    @Test
    void securityIdorAdminRoleAndZipBombProtectionAndObservability() throws Exception {
        // 普通用户越权访问管理运维接口返回 403
        mvc.perform(get("/api/v1/admin/logs").header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/search/consistency").header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isForbidden());

        // 用户A创建个人空间与目录，用户B跨用户 IDOR 访问必须被拦截（404 防枚举）
        long spaceA = IdWorker.getId();
        db.update("INSERT INTO space(id,owner_user_id,kind,name,description,icon,color,sort_order,created_by) VALUES (?,?,'PERSONAL','A私域空间','','','#6366f1',0,?)",
                spaceA, userAId, userAId);
        mvc.perform(get("/api/v1/spaces/" + spaceA + "/nodes").header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isNotFound());

        // 管理员可查看搜索一致性报告与链路观测指标
        mvc.perform(get("/api/v1/admin/search/consistency").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalFiles").isNumber());

        mvc.perform(get("/api/v1/admin/logs").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.metrics.totalRequests").isNumber());

        // Zip Bomb 压缩比保护测试（2MB 全零高压缩比文件触发 422 拦截）
        Path bombZip = Files.createTempFile("zip-bomb-test-", ".zip");
        try {
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(bombZip.toFile()))) {
                zos.putNextEntry(new ZipEntry("zeros.bin"));
                byte[] zeros = new byte[2 * 1024 * 1024];
                zos.write(zeros);
                zos.closeEntry();
            }
            assertThrows(BizException.class, () -> archivePreview.extractEntry(bombZip, "bomb.zip", "zeros.bin"));
        } finally {
            Files.deleteIfExists(bombZip);
        }
    }

    @Test
    void largeDirectoryPaginationLoadsOnlyRequestedPage() throws Exception {
        // 模拟大目录分页查询：批量插入 500 条子节点，验证分页接口只加载当前页 20 条必要节点
        long spaceId = IdWorker.getId();
        db.update("INSERT INTO space(id,owner_user_id,kind,name,description,icon,color,sort_order,created_by) VALUES (?,?,'PERSONAL','压测空间','','','#6366f1',0,?)",
                spaceId, userAId, userAId);
        long baseId = IdWorker.getId();
        List<Object[]> batch = new java.util.ArrayList<>(500);
        for (int i = 0; i < 500; i++) {
            String name = String.format("doc-%05d.txt", i);
            batch.add(new Object[]{baseId + i, spaceId, name, userAId});
        }
        db.batchUpdate(
                "INSERT INTO file_node(id,space_id,parent_id,node_type,name,created_by) VALUES (?,?,0,'FILE',?,?)",
                batch);

        long start = System.currentTimeMillis();
        mvc.perform(get("/api/v1/spaces/" + spaceId + "/nodes?page=1&pageSize=20").header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(500))
                .andExpect(jsonPath("$.data.items.length()").value(20));
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 3000, "分页查询耗时不应超过 3000ms，实际: " + elapsed + "ms");
    }
}
