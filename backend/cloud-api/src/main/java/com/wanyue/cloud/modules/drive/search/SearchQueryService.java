package com.wanyue.cloud.modules.drive.search;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.drive.DriveService;
import com.wanyue.cloud.modules.drive.search.SearchModels.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class SearchQueryService {
    private final JdbcTemplate db;
    private final DriveService drive;

    public SearchQueryService(JdbcTemplate db, DriveService drive) {
        this.db = db;
        this.drive = drive;
    }

    /**
     * Cross-space metadata + full-text chunk search with:
     * 1) Pre-filter by accessible spaces & shared folder roots (T139)
     * 2) Secondary per-candidate authorization check & pagination refill (T140)
     */
    public SearchResponse search(
            long user,
            String rawQuery,
            Long spaceIdFilter,
            String categoryFilter,
            String extensionFilter,
            boolean includeContent,
            int page,
            int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(50, pageSize));
        String q = rawQuery == null ? "" : rawQuery.strip();
        String cat = (categoryFilter == null || categoryFilter.isBlank()) ? null : categoryFilter.trim().toUpperCase(Locale.ROOT);
        String ext = (extensionFilter == null || extensionFilter.isBlank()) ? null : extensionFilter.trim().toLowerCase(Locale.ROOT).replaceFirst("^\\.", "");

        // 1. Permission pre-filter (T139): collect accessible space IDs & shared root node IDs
        List<Long> accessibleSpaces = db.queryForList(
                "SELECT s.id FROM space s "
                        + "LEFT JOIN space_member m ON m.space_id=s.id AND m.user_id=? AND m.deleted=0 "
                        + "  AND (m.expires_at IS NULL OR m.expires_at > UTC_TIMESTAMP(3)) "
                        + "WHERE s.deleted=0 AND s.status='ACTIVE' AND s.kind IN ('PERSONAL','SHARED') "
                        + "  AND (s.owner_user_id=? OR (s.kind='SHARED' AND m.id IS NOT NULL))",
                Long.class, user, user);

        List<Long> sharedRootNodes = db.queryForList(
                "SELECT rs.node_id FROM resource_share rs "
                        + "JOIN space s ON s.id=rs.space_id AND s.deleted=0 AND s.status='ACTIVE' "
                        + "WHERE rs.target_user_id=? AND rs.revoked_at IS NULL "
                        + "  AND (rs.expires_at IS NULL OR rs.expires_at > UTC_TIMESTAMP(3))",
                Long.class, user);

        if (accessibleSpaces.isEmpty() && sharedRootNodes.isEmpty()) {
            return new SearchResponse(List.of(), 0, safePage, safePageSize, false);
        }

        StringBuilder sql = new StringBuilder(
                "SELECT n.id, n.space_id, s.name space_name, n.parent_id, n.name, n.node_type, "
                        + "COALESCE(b.size_bytes, 0) size_bytes, COALESCE(v.mime_type, '') mime_type, n.updated_at ");
        List<Object> args = new ArrayList<>();

        if (!q.isEmpty() && includeContent) {
            sql.append(", (SELECT c.content FROM search_text_chunk c WHERE c.node_id=n.id AND LOCATE(LOWER(?), LOWER(c.content)) > 0 ORDER BY c.chunk_index ASC LIMIT 1) matched_chunk ");
            args.add(q);
        } else {
            sql.append(", NULL matched_chunk ");
        }

        sql.append("FROM file_node n JOIN space s ON s.id=n.space_id "
                + "LEFT JOIN file_version v ON v.id=n.current_version_id "
                + "LEFT JOIN blob_object b ON b.id=v.blob_id "
                + "WHERE n.deleted_at IS NULL AND n.hidden=FALSE AND s.deleted=0 AND s.status='ACTIVE' ");

        // Pre-filter scope predicate
        sql.append("AND (");
        boolean hasPrevScope = false;
        if (!accessibleSpaces.isEmpty()) {
            sql.append("n.space_id IN (").append(placeholders(accessibleSpaces.size())).append(")");
            args.addAll(accessibleSpaces);
            hasPrevScope = true;
        }
        if (!sharedRootNodes.isEmpty()) {
            if (hasPrevScope) sql.append(" OR ");
            sql.append("EXISTS(SELECT 1 FROM file_node_closure fnc WHERE fnc.space_id=n.space_id AND fnc.descendant_id=n.id AND fnc.ancestor_id IN (")
                    .append(placeholders(sharedRootNodes.size())).append("))");
            args.addAll(sharedRootNodes);
        }
        sql.append(") ");

        if (spaceIdFilter != null && spaceIdFilter > 0) {
            sql.append("AND n.space_id=? ");
            args.add(spaceIdFilter);
        }
        if (ext != null) {
            sql.append("AND n.node_type='FILE' AND LOWER(n.name) LIKE ? ");
            args.add("%." + ext);
        }
        if (!q.isEmpty()) {
            if (includeContent) {
                sql.append("AND (LOCATE(LOWER(?), LOWER(n.name)) > 0 OR EXISTS(SELECT 1 FROM search_text_chunk c WHERE c.node_id=n.id AND LOCATE(LOWER(?), LOWER(c.content)) > 0)) ");
                args.add(q);
                args.add(q);
            } else {
                sql.append("AND LOCATE(LOWER(?), LOWER(n.name)) > 0 ");
                args.add(q);
            }
        }
        sql.append("ORDER BY n.updated_at DESC, n.id DESC LIMIT 400");

        var candidates = db.queryForList(sql.toString(), args.toArray());

        // 2. Secondary per-candidate authorization + category filter + pagination refill (T140)
        List<SearchHit> authorized = new ArrayList<>();
        for (var row : candidates) {
            long sid = ((Number) row.get("space_id")).longValue();
            long nid = ((Number) row.get("id")).longValue();
            String name = String.valueOf(row.get("name"));
            String nodeType = String.valueOf(row.get("node_type"));
            String nodeCat = "FOLDER".equals(nodeType) ? "FOLDER" : DriveService.categorizeFile(name);
            if (cat != null && !cat.equals(nodeCat)) {
                continue;
            }
            try {
                drive.resolveNodeAccess(user, sid, nid, false, false);
            } catch (BizException ex) {
                continue;
            }
            String matchedChunk = row.get("matched_chunk") == null ? null : String.valueOf(row.get("matched_chunk"));
            boolean nameMatch = q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT));
            String matchSource = matchedChunk != null && !nameMatch ? "CONTENT" : (matchedChunk != null ? "BOTH" : "NAME");
            String snippet = buildSnippet(matchedChunk, q);
            String updatedAt = row.get("updated_at") instanceof LocalDateTime ldt ? ldt.toInstant(ZoneOffset.UTC).toString() : "";
            authorized.add(new SearchHit(
                    String.valueOf(nid),
                    String.valueOf(sid),
                    String.valueOf(row.get("space_name")),
                    String.valueOf(row.get("parent_id")),
                    name,
                    nodeType,
                    nodeCat,
                    ((Number) row.get("size_bytes")).longValue(),
                    String.valueOf(row.get("mime_type")),
                    updatedAt,
                    matchSource,
                    snippet));
        }

        long total = authorized.size();
        int from = Math.min((safePage - 1) * safePageSize, authorized.size());
        int to = Math.min(from + safePageSize, authorized.size());
        return new SearchResponse(authorized.subList(from, to), total, safePage, safePageSize, to < total);
    }

    private static String buildSnippet(String chunk, String q) {
        if (chunk == null || chunk.isBlank()) return "";
        if (q == null || q.isBlank()) return chunk.substring(0, Math.min(120, chunk.length()));
        String lower = chunk.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(q.toLowerCase(Locale.ROOT));
        if (idx < 0) return chunk.substring(0, Math.min(120, chunk.length()));
        int start = Math.max(0, idx - 36);
        int end = Math.min(chunk.length(), idx + q.length() + 64);
        String prefix = start > 0 ? "…" : "";
        String suffix = end < chunk.length() ? "…" : "";
        return prefix + chunk.substring(start, end).replaceAll("\\s+", " ").trim() + suffix;
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }
}
