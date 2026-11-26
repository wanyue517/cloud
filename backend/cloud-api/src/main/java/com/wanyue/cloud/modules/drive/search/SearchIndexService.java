package com.wanyue.cloud.modules.drive.search;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.modules.drive.LocalStorage;
import com.wanyue.cloud.modules.drive.search.SearchModels.IndexStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

@Service
public class SearchIndexService {
    private static final tools.jackson.databind.json.JsonMapper JSON = new tools.jackson.databind.json.JsonMapper();

    private final JdbcTemplate db;
    private final com.wanyue.cloud.modules.drive.storage.StorageRouterService storageRouter;
    private final TextExtractionService extractor;
    private final String meiliUrl;
    private final String meiliApiKey;
    private final int chunkChars;
    private final HttpClient httpClient;

    public SearchIndexService(
            JdbcTemplate db,
            com.wanyue.cloud.modules.drive.storage.StorageRouterService storageRouter,
            TextExtractionService extractor,
            @Value("${cloud.search.meilisearch-url:}") String meiliUrl,
            @Value("${cloud.search.meilisearch-api-key:}") String meiliApiKey,
            @Value("${cloud.search.chunk-chars:2000}") int chunkChars) {
        this.db = db;
        this.storageRouter = storageRouter;
        this.extractor = extractor;
        this.meiliUrl = meiliUrl == null ? "" : meiliUrl.strip().replaceAll("/+$", "");
        this.meiliApiKey = meiliApiKey == null ? "" : meiliApiKey.strip();
        this.chunkChars = Math.max(200, Math.min(10000, chunkChars));
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public static List<String> splitIntoChunks(String text, int maxChars) {
        if (text == null || text.isBlank()) return List.of();
        String normalized = text.replace("\r\n", "\n").trim();
        if (normalized.isEmpty()) return List.of();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        int len = normalized.length();
        while (start < len && chunks.size() < 200) {
            int end = Math.min(len, start + maxChars);
            chunks.add(normalized.substring(start, end));
            start = end;
        }
        return chunks;
    }

    @Scheduled(fixedDelay = 3000)
    public void scheduledConsume() {
        try {
            processPendingOutbox(50);
        } catch (Exception ignored) {}
    }

    public int processPendingOutbox(int batchSize) {
        var events = db.queryForList(
                "SELECT id, event_type, space_id, node_id, version_id FROM search_outbox WHERE status='PENDING' ORDER BY created_at ASC LIMIT ?",
                Math.max(1, batchSize));
        int processed = 0;
        for (var ev : events) {
            long id = ((Number) ev.get("id")).longValue();
            String type = String.valueOf(ev.get("event_type"));
            long spaceId = ((Number) ev.get("space_id")).longValue();
            long nodeId = ((Number) ev.get("node_id")).longValue();
            try {
                db.update("UPDATE search_outbox SET status='PROCESSING', attempt_count=attempt_count+1, updated_at=UTC_TIMESTAMP(3) WHERE id=?", id);
                if ("DELETE".equals(type)) {
                    deleteNodeIndex(nodeId);
                } else {
                    reindexNode(spaceId, nodeId);
                }
                db.update("UPDATE search_outbox SET status='DONE', error_message=NULL, updated_at=UTC_TIMESTAMP(3) WHERE id=?", id);
                processed++;
            } catch (Exception e) {
                String msg = e.getMessage() == null ? "索引失败" : e.getMessage();
                if (msg.length() > 900) msg = msg.substring(0, 900);
                db.update("UPDATE search_outbox SET status='FAILED', error_message=?, updated_at=UTC_TIMESTAMP(3) WHERE id=?", msg, id);
            }
        }
        return processed;
    }

    @Transactional
    public void deleteNodeIndex(long nodeId) {
        db.update("DELETE FROM search_text_chunk WHERE node_id=?", nodeId);
        deleteMeiliDoc(nodeId);
    }

    @Transactional
    public void reindexNode(long spaceId, long nodeId) {
        var rows = db.queryForList(
                "SELECT n.id, n.space_id, n.parent_id, n.name, n.node_type, n.hidden, n.deleted_at, n.current_version_id, "
                        + "COALESCE(b.storage_profile_id, 1) storage_profile_id, b.storage_key, COALESCE(b.size_bytes, 0) size_bytes "
                        + "FROM file_node n "
                        + "LEFT JOIN file_version v ON v.id=n.current_version_id "
                        + "LEFT JOIN blob_object b ON b.id=v.blob_id "
                        + "WHERE n.space_id=? AND n.id=?",
                spaceId, nodeId);
        if (rows.isEmpty()) {
            deleteNodeIndex(nodeId);
            return;
        }
        var row = rows.get(0);
        if (row.get("deleted_at") != null || Boolean.TRUE.equals(row.get("hidden"))) {
            deleteNodeIndex(nodeId);
            return;
        }
        db.update("DELETE FROM search_text_chunk WHERE node_id=?", nodeId);

        String name = String.valueOf(row.get("name"));
        String nodeType = String.valueOf(row.get("node_type"));
        List<String> chunks = List.of();
        long versionId = 0L;
        if ("FILE".equals(nodeType) && row.get("current_version_id") != null && row.get("storage_key") != null) {
            versionId = ((Number) row.get("current_version_id")).longValue();
            long sizeBytes = ((Number) row.get("size_bytes")).longValue();
            long profileId = ((Number) row.get("storage_profile_id")).longValue();
            Path path = storageRouter.resolveReadablePath(profileId, String.valueOf(row.get("storage_key")));
            String extracted = extractor.extractText(path, name, sizeBytes);
            chunks = splitIntoChunks(extracted, chunkChars);
            for (int i = 0; i < chunks.size(); i++) {
                db.update(
                        "INSERT INTO search_text_chunk(id, space_id, node_id, version_id, chunk_index, content) VALUES (?, ?, ?, ?, ?, ?)",
                        IdWorker.getId(), spaceId, nodeId, versionId, i, chunks.get(i));
            }
        }
        syncMeiliDoc(spaceId, nodeId, versionId, name, nodeType, chunks);
    }

    public int rebuildAll() {
        var nodes = db.queryForList(
                "SELECT n.space_id, n.id FROM file_node n JOIN space s ON s.id=n.space_id "
                        + "WHERE n.deleted_at IS NULL AND n.hidden=FALSE AND s.deleted=0 AND s.status='ACTIVE' LIMIT 5000");
        int count = 0;
        for (var n : nodes) {
            reindexNode(((Number) n.get("space_id")).longValue(), ((Number) n.get("id")).longValue());
            count++;
        }
        return count;
    }

    public int rebuildBySpace(long spaceId) {
        var nodes = db.queryForList(
                "SELECT n.space_id, n.id FROM file_node n JOIN space s ON s.id=n.space_id "
                        + "WHERE n.space_id=? AND n.deleted_at IS NULL AND n.hidden=FALSE AND s.deleted=0 AND s.status='ACTIVE' LIMIT 5000",
                spaceId);
        int count = 0;
        for (var n : nodes) {
            reindexNode(((Number) n.get("space_id")).longValue(), ((Number) n.get("id")).longValue());
            count++;
        }
        return count;
    }

    public int rebuildByNode(long nodeId) {
        var rows = db.queryForList("SELECT space_id FROM file_node WHERE id=?", nodeId);
        if (rows.isEmpty()) return 0;
        long spaceId = ((Number) rows.get(0).get("space_id")).longValue();
        reindexNode(spaceId, nodeId);
        return 1;
    }

    public IndexStatus status() {
        Long chunks = db.queryForObject("SELECT COUNT(*) FROM search_text_chunk", Long.class);
        Long files = db.queryForObject("SELECT COUNT(DISTINCT node_id) FROM search_text_chunk", Long.class);
        Long pending = db.queryForObject("SELECT COUNT(*) FROM search_outbox WHERE status='PENDING'", Long.class);
        Long failed = db.queryForObject("SELECT COUNT(*) FROM search_outbox WHERE status='FAILED'", Long.class);
        return new IndexStatus(
                chunks == null ? 0 : chunks,
                files == null ? 0 : files,
                pending == null ? 0 : pending,
                failed == null ? 0 : failed,
                !meiliUrl.isEmpty(),
                extractor.isTikaConfigured());
    }

    private void syncMeiliDoc(long spaceId, long nodeId, long versionId, String name, String nodeType, List<String> chunks) {
        if (meiliUrl.isEmpty()) return;
        try {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("id", String.valueOf(nodeId));
            doc.put("spaceId", String.valueOf(spaceId));
            doc.put("versionId", String.valueOf(versionId));
            doc.put("name", name);
            doc.put("nodeType", nodeType);
            doc.put("content", String.join("\n", chunks));
            String body = JSON.writeValueAsString(List.of(doc));
            var builder = HttpRequest.newBuilder()
                    .uri(URI.create(meiliUrl + "/indexes/cloud_resources/documents"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (!meiliApiKey.isEmpty()) builder.header("Authorization", "Bearer " + meiliApiKey);
            httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {}
    }

    private void deleteMeiliDoc(long nodeId) {
        if (meiliUrl.isEmpty()) return;
        try {
            var builder = HttpRequest.newBuilder()
                    .uri(URI.create(meiliUrl + "/indexes/cloud_resources/documents/" + nodeId))
                    .timeout(Duration.ofSeconds(5))
                    .DELETE();
            if (!meiliApiKey.isEmpty()) builder.header("Authorization", "Bearer " + meiliApiKey);
            httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {}
    }
}
