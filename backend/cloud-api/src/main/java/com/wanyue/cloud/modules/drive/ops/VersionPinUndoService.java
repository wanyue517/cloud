package com.wanyue.cloud.modules.drive.ops;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.drive.DriveModels;
import com.wanyue.cloud.modules.drive.DriveService;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class VersionPinUndoService {
    private static final tools.jackson.databind.json.JsonMapper JSON = new tools.jackson.databind.json.JsonMapper();

    private final JdbcTemplate db;
    private final DriveService drive;

    public VersionPinUndoService(JdbcTemplate db, DriveService drive) {
        this.db = db;
        this.drive = drive;
    }

    public record UserVersionPolicy(int maxVersions, int systemMaxVersions) {}
    public record UpdateUserVersionPolicyInput(@Min(1) @Max(200) int maxVersions) {}

    public record QuickAccessItem(String id, String spaceId, String spaceName, String nodeId, String name, String nodeType, long size, String updatedAt) {}

    public record UndoTokenReceipt(String tokenId, String actionType, String summary, String expiresAt) {}

    public record IssueUndoInput(
            @Positive long spaceId,
            @Pattern(regexp = "RENAME|MOVE|TRASH") String actionType,
            @NotBlank @Size(max = 255) String summary,
            @Positive long nodeId,
            String oldName,
            Long oldParentId) {}

    public record BatchRenameRequest(
            @NotEmpty @Size(max = 100) List<String> nodeIds,
            @Pattern(regexp = "PREFIX|SUFFIX|REPLACE|SEQUENCE") String mode,
            String prefix,
            String suffix,
            String find,
            String replace,
            String template,
            Integer startNumber,
            Boolean keepExtension,
            @Pattern(regexp = "ABORT|SKIP|AUTO_NUMBER") String conflictPolicy) {}

    public record BatchRenamePreviewItem(String nodeId, String oldName, String newName, boolean conflict, String reason) {}

    public record BatchRenameApplyResult(int renamedCount, int skippedCount, UndoTokenReceipt undo) {}

    public UserVersionPolicy getUserVersionPolicy(long user) {
        int sysMax = drive.getSystemMaxVersions();
        int eff = drive.getEffectiveMaxVersions(user);
        return new UserVersionPolicy(eff, sysMax);
    }

    @Transactional
    public UserVersionPolicy updateUserVersionPolicy(long user, int maxVersions) {
        int sysMax = drive.getSystemMaxVersions();
        if (maxVersions > sysMax) {
            throw new BizException(HttpStatus.BAD_REQUEST, 400, "个人版本上限不能超过系统最大值 (" + sysMax + ")");
        }
        db.update(
                "INSERT INTO user_version_policy(user_id, max_versions) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE max_versions=VALUES(max_versions), updated_at=UTC_TIMESTAMP(3)",
                user, maxVersions);
        return new UserVersionPolicy(maxVersions, sysMax);
    }

    @Transactional
    public void setQuickAccessPin(long user, long spaceId, long nodeId, boolean enabled) {
        if (!enabled) {
            db.update("DELETE FROM user_quick_access WHERE user_id=? AND node_id=?", user, nodeId);
            return;
        }
        drive.resolveNodeAccess(user, spaceId, nodeId, false, false);
        DriveModels.Node node = drive.getNode(spaceId, nodeId, false);
        if (node.hidden()) {
            throw BizException.conflict("隐藏项不能固定到快捷访问");
        }
        db.update("INSERT IGNORE INTO user_quick_access(id, user_id, space_id, node_id, sort_order) VALUES (?, ?, ?, ?, 0)",
                IdWorker.getId(), user, spaceId, nodeId);
    }

    /** Returns valid quick access pins and automatically prunes hidden, deleted, or revoked references (T128). */
    @Transactional
    public List<QuickAccessItem> listQuickAccess(long user) {
        var rows = db.queryForList(
                "SELECT qa.id, qa.space_id, qa.node_id, COALESCE(s.name, '') space_name, "
                        + "n.name, n.node_type, n.hidden, n.deleted_at, COALESCE(b.size_bytes, 0) size_bytes, n.updated_at "
                        + "FROM user_quick_access qa "
                        + "LEFT JOIN space s ON s.id=qa.space_id AND s.deleted=0 AND s.status='ACTIVE' "
                        + "LEFT JOIN file_node n ON n.id=qa.node_id "
                        + "LEFT JOIN file_version v ON v.id=n.current_version_id "
                        + "LEFT JOIN blob_object b ON b.id=v.blob_id "
                        + "WHERE qa.user_id=? ORDER BY qa.sort_order ASC, qa.created_at DESC",
                user);
        List<QuickAccessItem> valid = new ArrayList<>();
        List<Long> staleIds = new ArrayList<>();
        for (var r : rows) {
            long qaId = ((Number) r.get("id")).longValue();
            long spaceId = ((Number) r.get("space_id")).longValue();
            long nodeId = ((Number) r.get("node_id")).longValue();
            boolean missingOrHidden = r.get("name") == null || r.get("deleted_at") != null || Boolean.TRUE.equals(r.get("hidden"));
            if (missingOrHidden) {
                staleIds.add(qaId);
                continue;
            }
            try {
                drive.resolveNodeAccess(user, spaceId, nodeId, false, false);
            } catch (BizException ex) {
                staleIds.add(qaId);
                continue;
            }
            String updatedAt = r.get("updated_at") instanceof LocalDateTime ldt ? ldt.toInstant(ZoneOffset.UTC).toString() : "";
            valid.add(new QuickAccessItem(
                    String.valueOf(qaId),
                    String.valueOf(spaceId),
                    String.valueOf(r.get("space_name")),
                    String.valueOf(nodeId),
                    String.valueOf(r.get("name")),
                    String.valueOf(r.get("node_type")),
                    ((Number) r.get("size_bytes")).longValue(),
                    updatedAt));
        }
        for (Long staleId : staleIds) {
            db.update("DELETE FROM user_quick_access WHERE id=?", staleId);
        }
        return valid;
    }

    @Transactional
    public UndoTokenReceipt issueUndoToken(long user, long spaceId, String actionType, String summary, Map<String, Object> payload) {
        long tokenId = IdWorker.getId();
        LocalDateTime expires = LocalDateTime.ofInstant(Instant.now().plusSeconds(10), ZoneOffset.UTC);
        String json = JSON.writeValueAsString(payload);
        db.update(
                "INSERT INTO undo_token(id, user_id, space_id, action_type, summary, payload_json, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                tokenId, user, spaceId, actionType, summary, json, expires);
        return new UndoTokenReceipt(String.valueOf(tokenId), actionType, summary, expires.toInstant(ZoneOffset.UTC).toString());
    }

    @Transactional
    public void executeUndo(long user, long tokenId) {
        var rows = db.queryForList("SELECT * FROM undo_token WHERE id=? AND user_id=? FOR UPDATE", tokenId, user);
        if (rows.isEmpty()) throw BizException.notFound("撤销令牌不存在");
        var row = rows.get(0);
        if (row.get("consumed_at") != null) throw BizException.conflict("该操作已被撤销");
        LocalDateTime expires = (LocalDateTime) row.get("expires_at");
        if (expires.isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new BizException(HttpStatus.GONE, 410, "撤销窗口已超过 10 秒");
        }
        long spaceId = ((Number) row.get("space_id")).longValue();
        String actionType = String.valueOf(row.get("action_type"));
        var payload = JSON.readTree(String.valueOf(row.get("payload_json")));

        switch (actionType) {
            case "RENAME", "MOVE" -> {
                long nodeId = payload.get("nodeId").asLong();
                String oldName = payload.get("oldName").asString();
                long oldParentId = payload.get("oldParentId").asLong();
                DriveModels.Node current = drive.getNode(spaceId, nodeId, false);
                drive.change(user, spaceId, nodeId, new DriveModels.ChangeInput(oldName, oldParentId, current.revision()));
            }
            case "TRASH" -> {
                long nodeId = payload.get("nodeId").asLong();
                drive.restore(user, spaceId, nodeId);
            }
            case "BATCH_RENAME" -> {
                var items = payload.get("items");
                if (items != null && items.isArray()) {
                    for (var item : items) {
                        long nodeId = item.get("nodeId").asLong();
                        String oldName = item.get("oldName").asString();
                        DriveModels.Node current = drive.getNode(spaceId, nodeId, false);
                        drive.change(user, spaceId, nodeId, new DriveModels.ChangeInput(oldName, Long.parseLong(current.parentId()), current.revision()));
                    }
                }
            }
            default -> throw new BizException(HttpStatus.BAD_REQUEST, 400, "不支持的撤销类型");
        }
        db.update("UPDATE undo_token SET consumed_at=UTC_TIMESTAMP(3) WHERE id=?", tokenId);
    }

    public List<BatchRenamePreviewItem> previewBatchRename(long user, long spaceId, BatchRenameRequest req) {
        drive.access(user, spaceId, false, true);
        List<DriveModels.Node> targets = new ArrayList<>();
        Set<Long> selectedIds = new HashSet<>();
        for (String rawId : req.nodeIds()) {
            long nid = Long.parseLong(rawId);
            if (selectedIds.add(nid)) {
                targets.add(drive.getNode(spaceId, nid, false));
            }
        }
        if (targets.isEmpty()) return List.of();

        // Map existing sibling names per parentId (excluding the selected nodes themselves)
        Map<String, Set<String>> existingByParent = new HashMap<>();
        for (DriveModels.Node n : targets) {
            existingByParent.computeIfAbsent(n.parentId(), pid -> {
                var rows = db.queryForList(
                        "SELECT id, name FROM file_node WHERE space_id=? AND parent_id=? AND deleted_at IS NULL",
                        spaceId, Long.parseLong(pid));
                Set<String> names = new HashSet<>();
                for (var r : rows) {
                    long id = ((Number) r.get("id")).longValue();
                    if (!selectedIds.contains(id)) {
                        names.add(String.valueOf(r.get("name")).toLowerCase(Locale.ROOT));
                    }
                }
                return names;
            });
        }

        Map<String, Set<String>> claimedByParent = new HashMap<>();
        List<BatchRenamePreviewItem> preview = new ArrayList<>(targets.size());
        int seq = req.startNumber() == null ? 1 : req.startNumber();
        boolean keepExt = req.keepExtension() == null || req.keepExtension();

        for (DriveModels.Node n : targets) {
            String candidate = computeNewName(n.name(), n.nodeType().equals("FOLDER"), req, seq++, keepExt);
            boolean conflict = false;
            String reason = "";
            try {
                DriveService.validName(candidate);
            } catch (BizException ex) {
                conflict = true;
                reason = ex.getMessage();
            }
            String lower = candidate.toLowerCase(Locale.ROOT);
            Set<String> existing = existingByParent.getOrDefault(n.parentId(), Set.of());
            Set<String> claimed = claimedByParent.computeIfAbsent(n.parentId(), k -> new HashSet<>());
            if (!conflict && (existing.contains(lower) || claimed.contains(lower))) {
                if ("AUTO_NUMBER".equals(req.conflictPolicy())) {
                    candidate = deduplicateName(candidate, n.nodeType().equals("FOLDER"), existing, claimed);
                    claimed.add(candidate.toLowerCase(Locale.ROOT));
                } else {
                    conflict = true;
                    reason = "同目录存在同名冲突";
                }
            } else if (!conflict) {
                claimed.add(lower);
            }
            preview.add(new BatchRenamePreviewItem(n.id(), n.name(), candidate, conflict, reason));
        }
        return preview;
    }

    @Transactional
    public BatchRenameApplyResult applyBatchRename(long user, long spaceId, BatchRenameRequest req) {
        drive.access(user, spaceId, true, true);
        List<BatchRenamePreviewItem> plan = previewBatchRename(user, spaceId, req);
        String policy = req.conflictPolicy() == null ? "ABORT" : req.conflictPolicy();
        if ("ABORT".equals(policy) && plan.stream().anyMatch(BatchRenamePreviewItem::conflict)) {
            throw BizException.conflict("批量重命名存在同名冲突，请先调整规则或选择跳过/自动编号");
        }
        int renamed = 0;
        int skipped = 0;
        List<Map<String, Object>> undoItems = new ArrayList<>();
        // Two-pass rename via temporary names to allow swaps without intermediate unique collision
        List<BatchRenamePreviewItem> validItems = new ArrayList<>();
        for (BatchRenamePreviewItem item : plan) {
            if (item.conflict() || item.oldName().equals(item.newName())) {
                skipped++;
            } else {
                validItems.add(item);
            }
        }
        String salt = ".tmp-br-" + UUID.randomUUID().toString().substring(0, 8) + "-";
        for (BatchRenamePreviewItem item : validItems) {
            long nid = Long.parseLong(item.nodeId());
            DriveModels.Node cur = drive.getNode(spaceId, nid, false);
            drive.change(user, spaceId, nid, new DriveModels.ChangeInput(salt + nid, Long.parseLong(cur.parentId()), cur.revision()));
        }
        for (BatchRenamePreviewItem item : validItems) {
            long nid = Long.parseLong(item.nodeId());
            DriveModels.Node cur = drive.getNode(spaceId, nid, false);
            drive.change(user, spaceId, nid, new DriveModels.ChangeInput(item.newName(), Long.parseLong(cur.parentId()), cur.revision()));
            renamed++;
            undoItems.add(Map.of("nodeId", nid, "oldName", item.oldName()));
        }
        UndoTokenReceipt undo = undoItems.isEmpty()
                ? null
                : issueUndoToken(user, spaceId, "BATCH_RENAME", "批量重命名 " + renamed + " 项", Map.of("items", undoItems));
        return new BatchRenameApplyResult(renamed, skipped, undo);
    }

    private static String computeNewName(String oldName, boolean isFolder, BatchRenameRequest req, int seq, boolean keepExt) {
        String base = oldName;
        String ext = "";
        if (!isFolder && keepExt) {
            int dot = oldName.lastIndexOf('.');
            if (dot > 0 && dot < oldName.length() - 1) {
                base = oldName.substring(0, dot);
                ext = oldName.substring(dot);
            }
        }
        String mode = req.mode() == null ? "PREFIX" : req.mode();
        return switch (mode) {
            case "PREFIX" -> (req.prefix() == null ? "" : req.prefix()) + base + ext;
            case "SUFFIX" -> base + (req.suffix() == null ? "" : req.suffix()) + ext;
            case "REPLACE" -> {
                String find = req.find() == null ? "" : req.find();
                String rep = req.replace() == null ? "" : req.replace();
                String replaced = find.isEmpty() ? base : base.replace(find, rep);
                yield replaced + ext;
            }
            case "SEQUENCE" -> {
                String tpl = (req.template() == null || req.template().isBlank()) ? "文件_{n}" : req.template();
                String num = String.format("%03d", seq);
                yield tpl.replace("{n}", num) + ext;
            }
            default -> oldName;
        };
    }

    private static String deduplicateName(String candidate, boolean isFolder, Set<String> existing, Set<String> claimed) {
        String base = candidate;
        String ext = "";
        if (!isFolder) {
            int dot = candidate.lastIndexOf('.');
            if (dot > 0 && dot < candidate.length() - 1) {
                base = candidate.substring(0, dot);
                ext = candidate.substring(dot);
            }
        }
        for (int i = 1; i < 1000; i++) {
            String next = base + " (" + i + ")" + ext;
            String lower = next.toLowerCase(Locale.ROOT);
            if (!existing.contains(lower) && !claimed.contains(lower)) return next;
        }
        return base + "-" + UUID.randomUUID().toString().substring(0, 6) + ext;
    }
}
