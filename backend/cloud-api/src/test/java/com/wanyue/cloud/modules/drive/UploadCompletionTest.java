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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Run via scripts/verify-upload-completion.py against a disposable MySQL schema. */
@SpringBootTest(properties = {"cloud.upload.completion-worker-enabled=false", "cloud.openapi.enabled=true"})
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
    @Autowired DirectUploadService directUploads;
    @Autowired AdminController admin;
    @Autowired tools.jackson.databind.ObjectMapper objectMapper;
    @Autowired com.wanyue.cloud.modules.drive.storage.SecretCipher cipher;
    @MockitoSpyBean com.wanyue.cloud.modules.drive.storage.S3StorageProvider s3;
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
        db.update("UPDATE storage_profile SET is_default=(id=1)");
        user = IdWorker.getId(); space = IdWorker.getId();
        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?,'Upload test',1)", user);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)", IdWorker.getId(), user);
        db.update("INSERT INTO space(id,owner_user_id,kind,name) VALUES (?,?,'PERSONAL','Upload test')", space, user);
    }

    private long directUpload(String mode, boolean fallback) throws Exception {
        long profile = IdWorker.getId();
        db.update("UPDATE storage_profile SET is_default=FALSE");
        db.update("INSERT INTO storage_profile(id,name,provider,root_path,endpoint,bucket,region,access_key,secret_cipher,is_default,upload_mode,direct_threshold_bytes,direct_fallback) VALUES (?,'Direct test','S3','s3://test','https://storage.invalid','test','auto','access',?,TRUE,?,5,?)",
                profile, cipher.encrypt("secret"), mode, fallback);
        var session = uploads.create(user, new DriveModels.UploadInput(space, 0, "direct.bin", 6, "application/octet-stream"));
        doReturn("multipart-id").when(s3).initiateMultipart(any(), anyString(), anyString(), anyString());
        doReturn(java.util.List.of()).when(s3).listParts(any(), anyString(), anyString(), anyString());
        doNothing().when(s3).abortMultipart(any(), anyString(), anyString(), anyString());
        doNothing().when(s3).deleteObject(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        return Long.parseLong(session.id());
    }

    @Test
    void directUploadVerifiesRemotePartsAndCommitsWithoutReadingFileBytesOrTrustingClientHash() throws Exception {
        long id = directUpload("DIRECT", false);
        assertEquals("DIRECT", uploads.status(user, id, false).uploadMode());
        assertThrows(BizException.class, () -> directUploads.info(user + 1, id));
        directUploads.info(user, id);
        String url = (String) directUploads.presign(user, id, 0).get("uploadUrl");
        assertTrue(url.contains("partNumber=1"));
        assertTrue(url.contains("uploadId=multipart-id"));
        assertThrows(BizException.class, () -> directUploads.presign(user, id, 1));
        assertThrows(BizException.class, () -> uploads.chunk(user, id, 0, new ByteArrayInputStream(new byte[6]), 6));
        doReturn(java.util.List.of(new com.wanyue.cloud.modules.drive.storage.S3StorageProvider.RemotePart(1, 6, "\"etag\"")))
                .when(s3).listParts(any(), anyString(), anyString(), anyString());
        doNothing().when(s3).completeMultipart(any(), anyString(), anyString(), anyString(), anyList());
        doReturn(6L).when(s3).headObjectSize(any(), anyString(), anyString());
        uploads.requestCompletion(user, id, "0".repeat(64));
        assertTrue(worker.processNext());
        assertEquals("COMPLETED", uploads.status(user, id, false).status());
        assertNull(db.queryForObject("SELECT b.sha256 FROM blob_object b JOIN file_version v ON v.blob_id=b.id JOIN file_node n ON n.current_version_id=v.id WHERE n.space_id=?", String.class, space));
        verify(s3, never()).putObject(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyLong());
        assertEquals(0, drive.findUserReadableBlobBySha256(user, "0".repeat(64), 6).stream().count());
    }

    @Test
    void directModeIsPinnedAndFallbackRequiresExplicitOptIn() throws Exception {
        long id = directUpload("AUTO", false);
        assertEquals("DIRECT", uploads.status(user, id, false).uploadMode());
        db.update("UPDATE storage_profile SET upload_mode='PROXY'");
        assertEquals("DIRECT", uploads.status(user, id, false).uploadMode());
        assertThrows(BizException.class, () -> uploads.fallback(user, id));
        db.update("UPDATE upload_session SET direct_fallback=TRUE WHERE id=?", id);
        directUploads.info(user, id);
        assertEquals("PROXY", uploads.fallback(user, id).uploadMode());
        verify(s3).abortMultipart(any(), anyString(), anyString(), eq("multipart-id"));
        assertNull(db.queryForObject("SELECT direct_upload_id FROM upload_session WHERE id=?", String.class, id));
    }

    @Test
    void incompleteDirectUploadFailsWithoutCreatingNodeAndCanBeRetried() throws Exception {
        long id = directUpload("DIRECT", false);
        directUploads.info(user, id);
        uploads.requestCompletion(user, id, null);
        assertTrue(worker.processNext());
        assertEquals("FAILED", uploads.status(user, id, false).status());
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM file_node WHERE space_id=?", Long.class, space));
        directUploads.info(user, id);
        uploads.cancel(user, id);
        assertEquals("CANCELLED", uploads.status(user, id, false).status());
        verify(s3).abortMultipart(any(), anyString(), anyString(), eq("multipart-id"));
    }

    @Test
    void exportsActualApiContract() throws Exception {
        String json = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        // Only explicitly requested by the disposable test runner; normal tests do not rewrite documentation.
        if (System.getenv("EXPORT_UPLOAD_API_CONTRACT") != null) {
            var target = Path.of("..", "..", "docs", "api", "openapi.json");
            var parsed = objectMapper.readValue(json, java.util.Map.class);
            var baseline = objectMapper.readValue(Files.readString(target), java.util.Map.class);
            Files.writeString(target, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(orderLike(parsed, baseline)) + "\n", java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static Object orderLike(Object current, Object baseline) {
        if (current instanceof java.util.List<?> nowList && baseline instanceof java.util.List<?> oldList) {
            var result = new java.util.ArrayList<Object>();
            for (int i = 0; i < nowList.size(); i++) result.add(orderLike(nowList.get(i), i < oldList.size() ? oldList.get(i) : null));
            return result;
        }
        if (!(current instanceof java.util.Map<?, ?> now) || !(baseline instanceof java.util.Map<?, ?> old)) return current;
        var ordered = new java.util.LinkedHashMap<Object, Object>();
        for (var key : old.keySet()) if (now.containsKey(key)) ordered.put(key, orderLike(now.get(key), old.get(key)));
        for (var key : now.keySet()) if (!ordered.containsKey(key)) ordered.put(key, now.get(key));
        return ordered;
    }

    @Test
    void retriesCompletedRemoteObjectAfterMetadataFailureWithoutDeletingOrRetransmitting() throws Exception {
        long id = directUpload("DIRECT", false);
        directUploads.info(user, id);
        doReturn(java.util.List.of(new com.wanyue.cloud.modules.drive.storage.S3StorageProvider.RemotePart(1, 6, "\"etag\"")))
                .when(s3).listParts(any(), anyString(), anyString(), anyString());
        doNothing().when(s3).completeMultipart(any(), anyString(), anyString(), anyString(), anyList());
        doReturn(6L).when(s3).headObjectSize(any(), anyString(), anyString());
        doThrow(new IllegalStateException("metadata unavailable")).doCallRealMethod().when(uploads).finishCompletion(eq(user), eq(id), any());
        uploads.requestCompletion(user, id, null);
        assertTrue(worker.processNext());
        assertEquals("FAILED", uploads.status(user, id, false).status());
        verify(s3, never()).deleteObject(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        doThrow(new com.wanyue.cloud.modules.drive.storage.S3StorageProvider.S3ResponseException(404))
                .when(s3).listParts(any(), anyString(), anyString(), anyString());
        assertEquals(java.util.List.of(0), directUploads.info(user, id).get("received"));
        uploads.requestCompletion(user, id, null);
        assertTrue(worker.processNext());
        assertEquals("COMPLETED", uploads.status(user, id, false).status());
        verify(s3, times(1)).completeMultipart(any(), anyString(), anyString(), anyString(), anyList());
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(roles="SUPER_ADMIN")
    void adminCanReadAndConfigureUploadModeButCannotDeleteAnActiveStorageProfile() throws Exception {
        long id = directUpload("DIRECT", false);
        long profile = db.queryForObject("SELECT storage_profile_id FROM upload_session WHERE id=?", Long.class, id);
        assertFalse(admin.storageProfiles().getData().isEmpty());
        admin.updateStorageProfile(profile, new AdminController.StorageProfileInput("Updated", "S3", "s3://test", "https://storage.invalid", "test", "auto", "access", null, null,
                "AUTO", 200L * 1048576, 16777216, 2, 600, true));
        assertEquals("AUTO", db.queryForObject("SELECT upload_mode FROM storage_profile WHERE id=?", String.class, profile));
        assertEquals("DIRECT", uploads.status(user, id, false).uploadMode());
        db.update("UPDATE storage_profile SET is_default=FALSE WHERE id=?", profile);
        assertThrows(BizException.class, () -> admin.deleteStorageProfile(profile));
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
