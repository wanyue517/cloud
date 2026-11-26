package com.wanyue.cloud.modules.ops;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.common.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Consumer;

/**
 * 独立 Worker 消费者组调度与指数退避重试 / 死信队列服务（T165 / T166）。
 */
@Service
public class AsyncJobWorkerService {
    private final JdbcTemplate db;

    public AsyncJobWorkerService(JdbcTemplate db) {
        this.db = db;
    }

    public long enqueue(String consumerGroup, String jobType, String payload, int maxRetries) {
        long id = IdWorker.getId();
        int safeRetries = Math.max(1, Math.min(10, maxRetries));
        db.update(
                "INSERT INTO async_job(id, consumer_group, job_type, payload, status, retry_count, max_retries, next_retry_at) "
                        + "VALUES (?, ?, ?, ?, 'PENDING', 0, ?, UTC_TIMESTAMP(3))",
                id, consumerGroup, jobType, payload, safeRetries);
        return id;
    }

    /**
     * 按消费者组拉取一条到期任务并执行；失败按指数退避重入队，超过最大重试次数自动转入 DEAD_LETTER。
     */
    public boolean pollAndExecuteOne(String consumerGroup, Consumer<Map<String, Object>> handler) {
        var rows = db.queryForList(
                "SELECT id, consumer_group, job_type, payload, retry_count, max_retries "
                        + "FROM async_job WHERE consumer_group=? AND status='PENDING' AND next_retry_at <= UTC_TIMESTAMP(3) "
                        + "ORDER BY next_retry_at ASC, id ASC LIMIT 1",
                consumerGroup);
        if (rows.isEmpty()) return false;
        var job = rows.get(0);
        long id = ((Number) job.get("id")).longValue();
        int claimed = db.update(
                "UPDATE async_job SET status='RUNNING', updated_at=UTC_TIMESTAMP(3) WHERE id=? AND status='PENDING'",
                id);
        if (claimed != 1) return false;

        try {
            handler.accept(job);
            db.update("UPDATE async_job SET status='SUCCEEDED', last_error=NULL, updated_at=UTC_TIMESTAMP(3) WHERE id=?", id);
        } catch (Exception ex) {
            int retries = ((Number) job.get("retry_count")).intValue() + 1;
            int maxRetries = ((Number) job.get("max_retries")).intValue();
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            if (msg.length() > 950) msg = msg.substring(0, 950);
            if (retries >= maxRetries) {
                db.update(
                        "UPDATE async_job SET status='DEAD_LETTER', retry_count=?, last_error=?, updated_at=UTC_TIMESTAMP(3) WHERE id=?",
                        retries, msg, id);
            } else {
                int backoffSeconds = Math.min(300, (int) Math.pow(2, retries));
                db.update(
                        "UPDATE async_job SET status='PENDING', retry_count=?, last_error=?, "
                                + "next_retry_at=TIMESTAMPADD(SECOND, ?, UTC_TIMESTAMP(3)), updated_at=UTC_TIMESTAMP(3) WHERE id=?",
                        retries, msg, backoffSeconds, id);
            }
        }
        return true;
    }

    /** 立即强制执行指定任务（测试与手动补偿使用，忽略 next_retry_at 延迟） */
    public String executeImmediate(long jobId, Consumer<Map<String, Object>> handler) {
        var rows = db.queryForList(
                "SELECT id, consumer_group, job_type, payload, retry_count, max_retries FROM async_job WHERE id=?",
                jobId);
        if (rows.isEmpty()) throw BizException.notFound("异步任务不存在");
        var job = rows.get(0);
        db.update("UPDATE async_job SET status='RUNNING', updated_at=UTC_TIMESTAMP(3) WHERE id=?", jobId);
        try {
            handler.accept(job);
            db.update("UPDATE async_job SET status='SUCCEEDED', last_error=NULL, updated_at=UTC_TIMESTAMP(3) WHERE id=?", jobId);
            return "SUCCEEDED";
        } catch (Exception ex) {
            int retries = ((Number) job.get("retry_count")).intValue() + 1;
            int maxRetries = ((Number) job.get("max_retries")).intValue();
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            if (msg.length() > 950) msg = msg.substring(0, 950);
            if (retries >= maxRetries) {
                db.update(
                        "UPDATE async_job SET status='DEAD_LETTER', retry_count=?, last_error=?, updated_at=UTC_TIMESTAMP(3) WHERE id=?",
                        retries, msg, jobId);
                return "DEAD_LETTER";
            }
            db.update(
                    "UPDATE async_job SET status='PENDING', retry_count=?, last_error=?, next_retry_at=UTC_TIMESTAMP(3), updated_at=UTC_TIMESTAMP(3) WHERE id=?",
                    retries, msg, jobId);
            return "PENDING";
        }
    }

    @Transactional
    public void retryDeadLetter(long jobId) {
        int updated = db.update(
                "UPDATE async_job SET status='PENDING', retry_count=0, last_error=NULL, "
                        + "next_retry_at=UTC_TIMESTAMP(3), updated_at=UTC_TIMESTAMP(3) "
                        + "WHERE id=? AND status IN ('DEAD_LETTER', 'PENDING')",
                jobId);
        if (updated != 1) throw BizException.notFound("任务不存在或当前状态不可重试");
    }

    public Map<String, Object> listJobs(String status, String consumerGroup) {
        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("PENDING", 0L);
        summary.put("RUNNING", 0L);
        summary.put("SUCCEEDED", 0L);
        summary.put("DEAD_LETTER", 0L);
        for (var row : db.queryForList("SELECT status, COUNT(*) cnt FROM async_job GROUP BY status")) {
            summary.put(String.valueOf(row.get("status")), ((Number) row.get("cnt")).longValue());
        }
        StringBuilder sql = new StringBuilder(
                "SELECT CAST(id AS CHAR) id, consumer_group consumerGroup, job_type jobType, payload, status, "
                        + "retry_count retryCount, max_retries maxRetries, next_retry_at nextRetryAt, last_error lastError, "
                        + "created_at createdAt, updated_at updatedAt FROM async_job WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            sql.append(" AND status=?");
            params.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (consumerGroup != null && !consumerGroup.isBlank()) {
            sql.append(" AND consumer_group=?");
            params.add(consumerGroup.trim());
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT 100");
        return Map.of("summary", summary, "items", db.queryForList(sql.toString(), params.toArray()));
    }
}
