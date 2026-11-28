package com.wanyue.cloud.modules.drive;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.drive.personal.PrivateTokenFilter;
import com.wanyue.cloud.modules.drive.storage.SecretCipher;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Durable queue in upload_session; a connection-scoped MySQL lock limits disk concurrency to one.
 * A crashed process releases the lock, allowing another worker to resume PROCESSING sessions.
 * API instances must share the upload temporary directory, as for chunked upload itself.
 */
@Component
public class UploadCompletionWorker {
    private static final Logger log = LoggerFactory.getLogger(UploadCompletionWorker.class);
    private final JdbcTemplate db;
    private final UploadService uploads;
    private final DriveService drive;
    private final SecretCipher cipher;
    private final boolean enabled;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "upload-completion");
        thread.setDaemon(true);
        return thread;
    });

    public UploadCompletionWorker(JdbcTemplate db, UploadService uploads, DriveService drive, SecretCipher cipher,
                                  @Value("${cloud.upload.completion-worker-enabled:true}") boolean enabled) {
        this.db = db; this.uploads = uploads; this.drive = drive; this.cipher = cipher; this.enabled = enabled;
    }

    @PostConstruct
    public void start() {
        if (enabled) executor.scheduleWithFixedDelay(this::runSafely, 1, 1, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() { executor.shutdownNow(); }

    private void runSafely() {
        try { processNext(); }
        catch (Exception e) { log.error("Upload completion queue failed", e); }
    }

    public boolean processNext() throws Exception {
        // Do not retain a database transaction during file I/O. Named locks belong to this connection.
        try (Connection connection = db.getDataSource().getConnection()) {
            String lockName = "cloudlane:upload:" + connection.getCatalog();
            try (var statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                statement.setString(1, lockName);
                try (var result = statement.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) return false;
                }
            }
            try {
                var pending = db.queryForList("SELECT id,user_id,completion_private_token FROM upload_session WHERE status IN ('QUEUED','PROCESSING') ORDER BY completion_requested_at,id LIMIT 1");
                if (pending.isEmpty()) return false;
                var row = pending.get(0);
                long id = ((Number) row.get("id")).longValue();
                long user = ((Number) row.get("user_id")).longValue();
                try {
                    return PrivateTokenFilter.withToken(cipher.decrypt((String) row.get("completion_private_token")),
                            () -> process(user, id, connection, lockName));
                } catch (Exception e) {
                    // Shutdown/connection loss leaves the durable task for the next worker.
                    if (Thread.currentThread().isInterrupted() || !ownsLock(connection, lockName)) throw e;
                    String message = e instanceof BizException ? e.getMessage() : "文件合并或存储失败，请重试";
                    db.update("UPDATE upload_session SET status='FAILED',completion_error=?,completion_private_token=NULL WHERE id=? AND status IN ('QUEUED','PROCESSING')",
                            message.substring(0, Math.min(500, message.length())), id);
                    log.error("Upload completion failed: uploadId={}", id, e);
                    return true;
                }
            } finally {
                try (var statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                    statement.setString(1, lockName);
                    statement.execute();
                }
            }
        }
    }

    private boolean process(long user, long id, Connection connection, String lockName) throws Exception {
        var session = uploads.beginCompletion(user, id);
        if (session == null) return false;
        long started = System.nanoTime();
        var prepared = uploads.prepareCompletion(id, session);
        long stored = System.nanoTime();
        boolean committed = false;
        try {
            if (!ownsLock(connection, lockName)) throw new IllegalStateException("Upload worker lost its lock");
            uploads.finishCompletion(user, id, prepared);
            committed = true;
        } finally {
            if (!committed && !prepared.retainOnFailure()) {
                try { drive.discardPreparedUpload(prepared); }
                catch (Exception e) { log.warn("Upload object cleanup failed: uploadId={}", id, e); }
            }
        }
        long finished = System.nanoTime();
        boolean cleaned = uploads.cleanupCompletion(id, ((Number) session.get("chunk_count")).intValue());
        log.info("Upload completed: uploadId={}, bytes={}, storageMs={}, commitMs={}, cleanupMs={}, cleaned={}",
                id, session.get("size_bytes"), (stored - started) / 1_000_000,
                (finished - stored) / 1_000_000, (System.nanoTime() - finished) / 1_000_000, cleaned);
        return true;
    }

    private boolean ownsLock(Connection connection, String lockName) throws Exception {
        try (var statement = connection.prepareStatement("SELECT IS_USED_LOCK(?) = CONNECTION_ID()")) {
            statement.setString(1, lockName);
            try (var result = statement.executeQuery()) { return result.next() && result.getBoolean(1); }
        }
    }
}
