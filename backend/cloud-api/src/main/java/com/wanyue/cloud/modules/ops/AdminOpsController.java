package com.wanyue.cloud.modules.ops;

import com.wanyue.cloud.common.result.R;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 运维与可观测性后台接口（阶段 14: T165 / T166 / T167 / T172 / T173）。
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminOpsController {
    private final AsyncJobWorkerService jobs;
    private final StorageGcService gc;
    private final RequestObservabilityFilter observability;
    private final JdbcTemplate db;

    public AdminOpsController(
            AsyncJobWorkerService jobs,
            StorageGcService gc,
            RequestObservabilityFilter observability,
            JdbcTemplate db) {
        this.jobs = jobs;
        this.gc = gc;
        this.observability = observability;
        this.db = db;
    }

    @GetMapping("/jobs")
    public R<Map<String, Object>> listJobs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String consumerGroup) {
        return R.ok(jobs.listJobs(status, consumerGroup));
    }

    @PostMapping("/jobs/{id}/retry")
    public R<Void> retryJob(@PathVariable long id) {
        jobs.retryDeadLetter(id);
        return R.ok();
    }

    @PostMapping("/storage/gc")
    public R<Map<String, Object>> runStorageGc(
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam(defaultValue = "60") int graceMinutes) {
        return R.ok(gc.scanAndCollect(dryRun, graceMinutes));
    }

    @GetMapping("/search/consistency")
    public R<Map<String, Object>> searchConsistency() {
        Long totalFiles = db.queryForObject(
                "SELECT COUNT(*) FROM file_node WHERE node_type='FILE' AND deleted_at IS NULL", Long.class);
        Long indexedFiles = db.queryForObject(
                "SELECT COUNT(DISTINCT node_id) FROM search_text_chunk", Long.class);
        Long pendingOutbox = db.queryForObject(
                "SELECT COUNT(*) FROM search_outbox WHERE status='PENDING'", Long.class);
        Long orphanChunks = db.queryForObject(
                "SELECT COUNT(*) FROM search_text_chunk c LEFT JOIN file_node n ON n.id=c.node_id WHERE n.id IS NULL OR n.deleted_at IS NOT NULL",
                Long.class);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalFiles", totalFiles == null ? 0L : totalFiles);
        report.put("indexedFiles", indexedFiles == null ? 0L : indexedFiles);
        report.put("pendingOutbox", pendingOutbox == null ? 0L : pendingOutbox);
        report.put("orphanChunks", orphanChunks == null ? 0L : orphanChunks);
        report.put("consistent", (orphanChunks == null || orphanChunks == 0L) && (pendingOutbox == null || pendingOutbox == 0L));
        return R.ok(report);
    }

    @GetMapping("/logs")
    public R<Map<String, Object>> logs() {
        return R.ok(observability.snapshot());
    }
}
