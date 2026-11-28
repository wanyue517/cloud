package com.wanyue.cloud.modules.drive;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.drive.personal.PrivateTokenFilter;
import com.wanyue.cloud.modules.drive.storage.StorageRouterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Run via scripts/verify-upload-completion.py against a disposable MySQL schema. */
@SpringBootTest(properties = "cloud.upload.completion-worker-enabled=false")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "UPLOAD_TEST_DB_URL", matches = ".+")
class UploadCompletionTest {
    static final Path ROOT = Path.of("target", "upload-completion-tests", UUID.randomUUID().toString()).toAbsolutePath();
    @MockitoBean com.wanyue.cloud.common.config.RedisStartupCheck redisStartupCheck;
    @MockitoSpyBean UploadService uploads;
    @Autowired UploadCompletionWorker worker;
    @Autowired StorageRouterService storage;
    @Autowired JdbcTemplate db;
    @Autowired DriveService drive;
    @Autowired MockMvc mvc;
    @Autowired com.wanyue.cloud.modules.auth.support.JwtTokenProvider jwt;
    long user;
    long space;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry properties) {
        String url = System.getenv("UPLOAD_TEST_DB_URL");
        if (url == null || !url.matches("jdbc:mysql://[^/]+/cloud_upload_test_[a-f0-9]{12}\\?.*")) {
            throw new IllegalStateException("Use the disposable upload test runner");
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> System.getenv("UPLOAD_TEST_DB_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("UPLOAD_TEST_DB_PASSWORD"));
        properties.add("cloud.storage.root", () -> ROOT.resolve("objects").toString());
        properties.add("cloud.storage.upload-temp", () -> ROOT.resolve("uploads").toString());
        properties.add("cloud.storage.preview-root", () -> ROOT.resolve("previews").toString());
        properties.add("cloud.storage.worker-tmp", () -> ROOT.resolve("worker-tmp").toString());
    }

    @BeforeEach
    void setup() {
        user = IdWorker.getId(); space = IdWorker.getId();
        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?,'Upload test',1)", user);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)", IdWorker.getId(), user);
        db.update("INSERT INTO space(id,owner_user_id,kind,name) VALUES (?,?,'PERSONAL','Upload test')", space, user);
    }

    private long upload() throws Exception {
        long id = Long.parseLong(uploads.create(user, new DriveModels.UploadInput(space, 0, "file.bin", 6, "application/octet-stream")).id());
        // Exercise actual multi-part concatenation with tiny test chunks.
        db.update("UPDATE upload_session SET chunk_size=3,chunk_count=2 WHERE id=?", id);
        uploads.chunk(user, id, 0, new ByteArrayInputStream(new byte[]{1, 2, 3}), 3);
        uploads.chunk(user, id, 1, new ByteArrayInputStream(new byte[]{4, 5, 6}), 3);
        return id;
    }

    private String hash() throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new byte[]{1, 2, 3, 4, 5, 6}));
    }

    @Test
    void queuesIdempotentlyAndStoresOutsideTransactionThenCommitsExactContent() throws Exception {
        long id = upload();
        mvc.perform(post("/api/v1/uploads/" + id + "/finalize")
                        .header("Authorization", "Bearer " + jwt.createAccessToken(user, 0))
                        .param("expectedSha256", hash()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("QUEUED"));
        assertEquals("QUEUED", uploads.requestCompletion(user, id, hash()).status());
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM file_node WHERE space_id=?", Long.class, space));
        doAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "File I/O must not hold a transaction");
            return call.callRealMethod();
        }).when(uploads).prepareCompletion(eq(id), anyMap());
        assertTrue(worker.processNext());
        var result = uploads.status(user, id, false);
        assertEquals("COMPLETED", result.status());
        assertEquals(2, result.received().size());
        assertTrue(result.missing().isEmpty(), "Cleaned chunks must not appear missing after completion");
        assertEquals(result.fileId(), uploads.requestCompletion(user, id, null).fileId());
        var blob = db.queryForMap("SELECT b.storage_profile_id,b.storage_key,b.sha256 FROM blob_object b JOIN file_version v ON v.blob_id=b.id WHERE v.file_id=?", Long.parseLong(result.fileId()));
        assertEquals(hash(), blob.get("sha256"));
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6}, Files.readAllBytes(storage.resolveReadablePath(((Number) blob.get("storage_profile_id")).longValue(), (String) blob.get("storage_key"))));
        assertFalse(Files.exists(ROOT.resolve("uploads").resolve(id + "-0")));
        assertFalse(worker.processNext());
    }

    @Test
    void legacyCompletionAndDirectUploadRemainCompatible() throws Exception {
        long id = upload();
        String fileId = uploads.complete(user, id, hash());
        assertEquals(fileId, uploads.complete(user, id, hash()));
        assertEquals("COMPLETED", uploads.status(user, id, false).status());
        var direct = drive.upload(user, space, 0, "direct.bin", "application/octet-stream",
                new ByteArrayInputStream(new byte[]{7, 8}), 2, null, null);
        assertNotNull(direct.id());
        assertEquals(2L, db.queryForObject("SELECT COUNT(*) FROM file_node WHERE space_id=?", Long.class, space));
    }

    @Test
    void resumesProcessingAfterRestartAndDoesNotExpireActiveWork() throws Exception {
        long id = upload();
        uploads.requestCompletion(user, id, null);
        db.update("UPDATE upload_session SET status='PROCESSING',expires_at=DATE_SUB(UTC_TIMESTAMP(3),INTERVAL 1 HOUR) WHERE id=?", id);
        uploads.cleanExpiredAndCount();
        assertEquals("PROCESSING", uploads.status(user, id, false).status());
        assertTrue(worker.processNext());
        assertEquals("COMPLETED", uploads.status(user, id, false).status());
    }

    @Test
    void rejectsMissingChunksAndOtherUsers() throws Exception {
        long id = upload();
        assertThrows(BizException.class, () -> uploads.requestCompletion(user + 1, id, null));
        Files.delete(ROOT.resolve("uploads").resolve(id + "-1"));
        assertThrows(BizException.class, () -> uploads.requestCompletion(user, id, null));
        assertEquals("UPLOADING", uploads.status(user, id, false).status());
    }

    @Test
    void reportsChecksumFailureAndAllowsRetryWithRetainedChunks() throws Exception {
        long id = upload();
        uploads.requestCompletion(user, id, "0".repeat(64));
        assertTrue(worker.processNext());
        var failed = uploads.status(user, id, false);
        assertEquals("FAILED", failed.status());
        assertTrue(failed.errorMessage().contains("SHA-256"));
        assertEquals(2, failed.received().size());
        uploads.requestCompletion(user, id, hash());
        assertTrue(worker.processNext());
        assertEquals("COMPLETED", uploads.status(user, id, false).status());
    }

    @Test
    void cancellationDuringStorageDoesNotCreateFile() throws Exception {
        long id = upload();
        uploads.requestCompletion(user, id, null);
        doAnswer(call -> {
            var prepared = call.callRealMethod();
            uploads.cancel(user, id);
            return prepared;
        }).when(uploads).prepareCompletion(eq(id), anyMap());
        assertTrue(worker.processNext());
        assertEquals("CANCELLED", uploads.status(user, id, false).status());
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM file_node WHERE space_id=?", Long.class, space));
    }

    @Test
    void databaseLockPreventsConcurrentWorkers() throws Exception {
        long id = upload();
        uploads.requestCompletion(user, id, null);
        try (var connection = db.getDataSource().getConnection(); var statement = connection.createStatement()) {
            String name = "cloudlane:upload:" + connection.getCatalog();
            statement.executeQuery("SELECT GET_LOCK('" + name + "',0)").close();
            try { assertFalse(worker.processNext()); }
            finally { statement.executeQuery("SELECT RELEASE_LOCK('" + name + "')").close(); }
        }
        assertTrue(worker.processNext());
    }

    @Test
    void restoresPrivateTokenAndClearsItAfterBackgroundWork() throws Exception {
        long id = upload();
        db.update("UPDATE space SET kind='PRIVATE' WHERE id=?", space);
        String token = "private-upload-test-token";
        db.update("INSERT INTO private_space_session(id,user_id,space_id,token_hash,idle_timeout_minutes,expires_at) VALUES (?,?,?,?,30,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 30 MINUTE))",
                IdWorker.getId(), user, space, com.wanyue.cloud.modules.drive.sharelink.ShareLinkService.sha256Hex(token));
        PrivateTokenFilter.withToken(token, () -> uploads.requestCompletion(user, id, null));
        String encrypted = db.queryForObject("SELECT completion_private_token FROM upload_session WHERE id=?", String.class, id);
        assertTrue(encrypted.startsWith("enc:v1:"));
        assertFalse(encrypted.contains(token));
        assertTrue(worker.processNext());
        assertNull(PrivateTokenFilter.currentToken());
        assertEquals("COMPLETED", PrivateTokenFilter.withToken(token, () -> uploads.status(user, id, false)).status());
        assertNull(db.queryForObject("SELECT completion_private_token FROM upload_session WHERE id=?", String.class, id));
    }
}
