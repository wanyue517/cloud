package com.wanyue.cloud.modules.drive;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.common.exception.BizException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.*;
import java.util.*;
import static com.wanyue.cloud.modules.drive.DriveModels.*;

@Service
public class DriveService {
    private final JdbcTemplate db;
    private final LocalStorage storage;
    private final com.wanyue.cloud.modules.drive.storage.StorageRouterService storageRouter;
    private final com.wanyue.cloud.modules.ops.SystemPolicyService policy;
    public DriveService(
            JdbcTemplate db,
            LocalStorage storage,
            com.wanyue.cloud.modules.drive.storage.StorageRouterService storageRouter,
            com.wanyue.cloud.modules.ops.SystemPolicyService policy) {
        this.db=db; this.storage=storage; this.storageRouter=storageRouter; this.policy=policy;
    }
    public long usedBytes(long user) {
        Long used = db.queryForObject(
                "SELECT COALESCE(SUM(b.size_bytes),0) FROM file_node n "
                        + "JOIN space s ON s.id=n.space_id "
                        + "JOIN file_version v ON v.file_id=n.id "
                        + "JOIN blob_object b ON b.id=v.blob_id "
                        + "WHERE s.owner_user_id=? AND s.deleted=0 AND s.status='ACTIVE'",
                Long.class, user);
        return used == null ? 0L : used;
    }
    public void checkQuota(long user, long incomingBytes) {
        long quotaBytes = policy.userDefaultQuotaBytes();
        if (quotaBytes > 0 && usedBytes(user) + Math.max(0L, incomingBytes) > quotaBytes) {
            throw new BizException(HttpStatus.PAYLOAD_TOO_LARGE, 20001, "存储空间容量不足");
        }
    }
    public void checkUploadPolicy(String fileName, long sizeBytes) {
        long maxFileBytes = policy.uploadMaxFileBytes();
        if (maxFileBytes > 0 && sizeBytes > maxFileBytes) {
            throw new BizException(HttpStatus.PAYLOAD_TOO_LARGE, 413,
                    "单文件大小超出系统上限（当前上限 " + policy.uploadMaxFileMb() + " MB）");
        }
        Set<String> blocked = policy.getBlockedExtensions();
        if (!blocked.isEmpty() && fileName != null && fileName.contains(".")) {
            String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (blocked.contains(ext)) {
                throw new BizException(HttpStatus.FORBIDDEN, 403, "系统策略禁止上传 ." + ext + " 类型文件");
            }
        }
    }
    private static final String NODE_SELECT = "SELECT n.*,COALESCE(b.size_bytes,0) size_bytes,COALESCE(v.mime_type,'') mime_type,"
            + "EXISTS(SELECT 1 FROM file_node c WHERE c.space_id=n.space_id AND c.parent_id=n.id AND c.deleted_at IS NULL AND c.node_type='FOLDER') has_children "
            + "FROM file_node n LEFT JOIN file_version v ON v.id=n.current_version_id LEFT JOIN blob_object b ON b.id=v.blob_id ";
    private static final RowMapper<Node> NODE_MAPPER = (r,i) -> new Node(r.getString("id"),r.getString("space_id"),
            r.getString("parent_id"),r.getString("name"),r.getString("node_type"),r.getString("current_version_id"),
            r.getLong("revision"),r.getLong("size_bytes"),r.getString("mime_type"),r.getBoolean("hidden"),
            r.getObject("updated_at",java.time.LocalDateTime.class).toInstant(java.time.ZoneOffset.UTC).toString(),r.getBoolean("has_children"));
    public static String validName(String name) {
        if (name == null || name.isBlank() || name.length()>255 || !name.equals(name.strip())
                || name.equals(".") || name.equals("..") || name.chars().anyMatch(Character::isISOControl)
                || name.matches(".*[\\\\/:*?\"<>|].*"))
            throw new BizException(HttpStatus.BAD_REQUEST,400,"名称为空或含非法字符");
        return name;
    }
    public record NodeAccess(String role, Long sharedRootId) {
        public boolean canWrite() {
            return "OWNER".equals(role) || "MANAGER".equals(role) || "EDITOR".equals(role);
        }
    }

    /** Resolve space-level role (OWNER for owner, or active space_member role for SHARED space; PRIVATE requires unlocked session). */
    public String resolveSpaceRole(long user, long space, boolean lock) {
        var rows = db.queryForList(
                "SELECT s.owner_user_id, s.kind, m.role member_role "
                        + "FROM space s "
                        + "LEFT JOIN space_member m ON m.space_id=s.id AND m.user_id=? AND m.deleted=0 "
                        + "  AND (m.expires_at IS NULL OR m.expires_at > UTC_TIMESTAMP(3)) "
                        + "WHERE s.id=? AND s.deleted=0 AND s.status='ACTIVE' AND s.kind IN ('PERSONAL','SHARED','PRIVATE')"
                        + (lock ? " FOR UPDATE" : ""),
                user, space);
        if (rows.isEmpty()) return null;
        var row = rows.get(0);
        long ownerId = ((Number) row.get("owner_user_id")).longValue();
        String kind = String.valueOf(row.get("kind"));
        if ("PRIVATE".equals(kind)) {
            if (ownerId != user) return null;
            String token = com.wanyue.cloud.modules.drive.personal.PrivateTokenFilter.currentToken();
            if (token == null || token.isBlank()) {
                throw new BizException(HttpStatus.LOCKED, 423, "私密空间已锁定，请先输入密码解锁");
            }
            String hash = com.wanyue.cloud.modules.drive.sharelink.ShareLinkService.sha256Hex(token);
            var sess = db.queryForList(
                    "SELECT id, idle_timeout_minutes FROM private_space_session "
                            + "WHERE space_id IN (?, 0) AND user_id=? AND token_hash=? AND revoked_at IS NULL AND expires_at > UTC_TIMESTAMP(3) LIMIT 1",
                    space, user, hash);
            if (sess.isEmpty()) {
                throw new BizException(HttpStatus.LOCKED, 423, "私密空间授权已过期或已锁定，请重新解锁");
            }
            int idleMin = ((Number) sess.get(0).get("idle_timeout_minutes")).intValue();
            db.update("UPDATE private_space_session SET expires_at=DATE_ADD(UTC_TIMESTAMP(3), INTERVAL ? MINUTE) WHERE id=?",
                    idleMin, ((Number) sess.get(0).get("id")).longValue());
            return "OWNER";
        }
        if (ownerId == user) return "OWNER";
        if ("SHARED".equals(kind) && row.get("member_role") != null) {
            return String.valueOf(row.get("member_role"));
        }
        return null;
    }

    /** 校验个人中心「私密设置」全局解锁会话（space_id=0），并按空闲超时续期。 */
    public void requireVaultSession(long user) {
        String token = com.wanyue.cloud.modules.drive.personal.PrivateTokenFilter.currentToken();
        if (token == null || token.isBlank()) {
            throw new BizException(HttpStatus.LOCKED, 423, "私密设置已锁定，请先输入密码解锁");
        }
        String hash = com.wanyue.cloud.modules.drive.sharelink.ShareLinkService.sha256Hex(token);
        var sess = db.queryForList(
                "SELECT id, idle_timeout_minutes FROM private_space_session "
                        + "WHERE space_id=0 AND user_id=? AND token_hash=? AND revoked_at IS NULL AND expires_at > UTC_TIMESTAMP(3) LIMIT 1",
                user, hash);
        if (sess.isEmpty()) {
            throw new BizException(HttpStatus.LOCKED, 423, "私密设置授权已过期或已锁定，请重新验证密码");
        }
        int idleMin = ((Number) sess.get(0).get("idle_timeout_minutes")).intValue();
        db.update("UPDATE private_space_session SET expires_at=DATE_ADD(UTC_TIMESTAMP(3), INTERVAL ? MINUTE) WHERE id=?",
                idleMin, ((Number) sess.get(0).get("id")).longValue());
    }

    /** Resolve node-level access: space role first, then nearest ancestor resource_share via closure table. */
    public NodeAccess resolveNodeAccess(long user, long space, long nodeId, boolean requireWrite, boolean lock) {
        String spaceRole = resolveSpaceRole(user, space, lock);
        if (spaceRole != null) {
            NodeAccess access = new NodeAccess(spaceRole, null);
            if (requireWrite && !access.canWrite()) {
                throw new BizException(HttpStatus.FORBIDDEN, 403, "当前角色仅支持只读访问");
            }
            return access;
        }
        if (nodeId <= 0) {
            throw BizException.notFound("空间不存在或无权访问");
        }
        var shareRows = db.queryForList(
                "SELECT rs.node_id, rs.permission "
                        + "FROM file_node_closure c "
                        + "JOIN resource_share rs ON rs.space_id=c.space_id AND rs.node_id=c.ancestor_id "
                        + "JOIN space s ON s.id=rs.space_id AND s.deleted=0 AND s.status='ACTIVE' "
                        + "WHERE c.space_id=? AND c.descendant_id=? AND rs.target_user_id=? "
                        + "  AND rs.revoked_at IS NULL AND (rs.expires_at IS NULL OR rs.expires_at > UTC_TIMESTAMP(3)) "
                        + "ORDER BY c.depth ASC LIMIT 1",
                space, nodeId, user);
        if (shareRows.isEmpty()) {
            throw BizException.notFound("空间不存在或无权访问");
        }
        long rootId = ((Number) shareRows.get(0).get("node_id")).longValue();
        String perm = String.valueOf(shareRows.get(0).get("permission"));
        NodeAccess access = new NodeAccess(perm, rootId);
        if (requireWrite && !access.canWrite()) {
            throw new BizException(HttpStatus.FORBIDDEN, 403, "当前共享权限仅支持只读访问");
        }
        return access;
    }

    /** Lock the owning space for all tree mutations: prevents races with moves and deletes. */
    public void access(long user, long space, boolean lock) {
        access(user, space, lock, lock);
    }

    public void access(long user, long space, boolean lock, boolean requireWrite) {
        String role = resolveSpaceRole(user, space, lock);
        if (role == null) throw BizException.notFound("空间不存在或无权访问");
        if (requireWrite && !"OWNER".equals(role) && !"MANAGER".equals(role) && !"EDITOR".equals(role)) {
            throw new BizException(HttpStatus.FORBIDDEN, 403, "当前角色无写入权限");
        }
    }

    public void requireSpaceOwner(long user, long space, boolean lock) {
        String role = resolveSpaceRole(user, space, lock);
        if (!"OWNER".equals(role)) throw BizException.notFound("空间不存在或仅所有者可操作");
    }

    private void lockSpacesOrdered(long user, long first, long second) {
        if (first == second) {
            access(user, first, true, true);
            return;
        }
        long lo = Math.min(first, second), hi = Math.max(first, second);
        access(user, lo, true, true);
        access(user, hi, true, true);
    }
    public List<Space> spaces(long user) {
        return db.query(
                "SELECT s.id, s.name, s.kind, s.icon, s.color, s.description, s.sort_order, "
                        + "CASE WHEN s.owner_user_id=? THEN 'OWNER' ELSE m.role END AS effective_role, "
                        + "COALESCE(u.display_name, '') AS owner_name "
                        + "FROM space s "
                        + "LEFT JOIN sys_user u ON u.id=s.owner_user_id "
                        + "LEFT JOIN space_member m ON m.space_id=s.id AND m.user_id=? AND m.deleted=0 "
                        + "  AND (m.expires_at IS NULL OR m.expires_at > UTC_TIMESTAMP(3)) "
                        + "WHERE s.deleted=0 AND s.status='ACTIVE' AND s.kind IN ('PERSONAL','SHARED','PRIVATE') "
                        + "  AND (s.owner_user_id=? OR (s.kind='SHARED' AND m.id IS NOT NULL)) "
                        + "ORDER BY s.sort_order, s.id",
                (r,i)->new Space(
                        r.getString("id"),
                        r.getString("name"),
                        r.getString("kind"),
                        r.getString("icon") == null ? "" : r.getString("icon"),
                        r.getString("color"),
                        r.getString("description") == null ? "" : r.getString("description"),
                        r.getLong("sort_order"),
                        r.getString("effective_role"),
                        r.getString("owner_name")),
                user, user, user);
    }
    @Transactional
    public String createSpace(long user, SpaceInput input) {
        long id=IdWorker.getId();
        String kind = "SHARED".equals(input.kind()) ? "SHARED" : ("PRIVATE".equals(input.kind()) ? "PRIVATE" : "PERSONAL");
        db.update("INSERT INTO space(id,owner_user_id,kind,name,description,icon,color,sort_order,created_by) VALUES (?,?,?,?,?,?,?,?,?)",
                id,user,kind,validName(input.name()),input.description()==null?"":input.description(),
                input.icon()==null?"":input.icon(),
                input.color()==null?"#6366f1":input.color(),input.sortOrder()==null?0:input.sortOrder(),user);
        if ("SHARED".equals(kind)) {
            db.update("INSERT INTO space_member(id,space_id,user_id,role,created_by,updated_by) VALUES (?,?,?,'OWNER',?,?)",
                    IdWorker.getId(), id, user, user, user);
        }
        return String.valueOf(id);
    }
    @Transactional
    public void updateSpace(long user,long space,SpaceInput input) {
        requireSpaceOwner(user,space,true);
        db.update("UPDATE space SET name=?,description=?,icon=?,color=?,sort_order=?,version=version+1 WHERE id=?",
                validName(input.name()),input.description()==null?"":input.description(),
                input.icon()==null?"":input.icon(),
                input.color()==null?"#6366f1":input.color(),input.sortOrder()==null?0:input.sortOrder(),space);
    }
    @Transactional
    public void deleteSpace(long user,long space,String confirmation) {
        requireSpaceOwner(user,space,true);
        String name=db.queryForObject("SELECT name FROM space WHERE id=?",String.class,space);
        if (!Objects.equals(name,confirmation)) throw new BizException(HttpStatus.BAD_REQUEST,400,"请输入空间名称确认删除");
        db.update("UPDATE space SET status='DELETED',deleted=id,delete_requested_at=UTC_TIMESTAMP(3),deleted_at=UTC_TIMESTAMP(3),purge_after=DATE_ADD(UTC_TIMESTAMP(3), INTERVAL ? DAY),access_version=access_version+1 WHERE id=?",
                policy.trashRetentionDays(), space);
    }
    private void parent(long space,long parent) {
        if (parent==0) return;
        Node n=getNode(space,parent,false);
        if (!n.nodeType().equals("FOLDER")) throw BizException.notFound("目标文件夹不存在");
        ancestors(space,parent);
    }
    public Node getNode(long space,long node,boolean deleted) {
        var rows=db.query(NODE_SELECT+"WHERE n.space_id=? AND n.id=? AND n.deleted_at IS "+(deleted?"NOT NULL":"NULL"),NODE_MAPPER,space,node);
        if(rows.isEmpty()) throw BizException.notFound("文件不存在");
        return rows.get(0);
    }
    public List<Node> ancestors(long space,long node) {
        List<Node> result=new ArrayList<>();
        Set<Long> seen=new HashSet<>();
        int maxDepth = policy.maxDirectoryDepth();
        while(node!=0) {
            if(!seen.add(node) || seen.size()>maxDepth) throw BizException.conflict("目录层级异常或超过系统深度限制(" + maxDepth + ")");
            Node n=getNode(space,node,false); result.add(n); node=Long.parseLong(n.parentId());
        }
        Collections.reverse(result); return result;
    }
    /** User-scoped ancestors: truncates at shared root so unauthorized parent folders are never exposed (T106). */
    public List<Node> ancestorsForUser(long user, long space, long node) {
        NodeAccess access = resolveNodeAccess(user, space, node, false, false);
        List<Node> full = ancestors(space, node);
        if (access.sharedRootId() == null) return full;
        String rootStr = String.valueOf(access.sharedRootId());
        for (int i = 0; i < full.size(); i++) {
            if (rootStr.equals(full.get(i).id())) {
                return new ArrayList<>(full.subList(i, full.size()));
            }
        }
        throw BizException.notFound("空间不存在或无权访问");
    }
    private void unique(long space,long parent,String name,long except) {
        if(db.queryForObject("SELECT COUNT(*) FROM file_node WHERE space_id=? AND parent_id=? AND name=? AND deleted_at IS NULL AND id<>?",Long.class,space,parent,name,except)>0)
            throw BizException.conflict("此目录已有同名文件或文件夹");
    }
    private void insertClosure(long space, long nodeId, long parentId) {
        db.update("INSERT IGNORE INTO file_node_closure(space_id,ancestor_id,descendant_id,depth) VALUES (?,?,?,0)", space, nodeId, nodeId);
        if (parentId > 0) {
            db.update("INSERT IGNORE INTO file_node_closure(space_id,ancestor_id,descendant_id,depth) "
                    + "SELECT ?, ancestor_id, ?, depth + 1 FROM file_node_closure WHERE space_id=? AND descendant_id=?",
                    space, nodeId, space, parentId);
        }
    }
    private void moveClosureWithinSpace(long space, long nodeId, long newParentId) {
        // 1. 删除子树节点指向子树外部旧祖先的闭包边，保留子树内部全部边
        db.update("DELETE c FROM file_node_closure c "
                + "JOIN (SELECT descendant_id FROM file_node_closure WHERE space_id=? AND ancestor_id=?) sub ON c.descendant_id=sub.descendant_id "
                + "LEFT JOIN (SELECT descendant_id FROM file_node_closure WHERE space_id=? AND ancestor_id=?) sub_anc ON c.ancestor_id=sub_anc.descendant_id "
                + "WHERE c.space_id=? AND sub_anc.descendant_id IS NULL",
                space, nodeId, space, nodeId, space);
        // 2. 若移入非根目录，挂接新父节点的所有祖先到子树所有节点
        if (newParentId > 0) {
            db.update("INSERT IGNORE INTO file_node_closure(space_id,ancestor_id,descendant_id,depth) "
                    + "SELECT ?, a.ancestor_id, d.descendant_id, a.depth + d.depth + 1 "
                    + "FROM file_node_closure a JOIN file_node_closure d ON a.space_id=? AND d.space_id=? "
                    + "WHERE a.descendant_id=? AND d.ancestor_id=?",
                    space, space, space, newParentId, nodeId);
        }
    }
    public DirectoryStats stats(long user, long space, long parent, boolean hidden) {
        return stats(user, space, parent, hidden, false);
    }
    public DirectoryStats stats(long user, long space, long parent, boolean hidden, boolean includeSubdirs) {
        resolveNodeAccess(user, space, parent, false, false);
        parent(space, parent);
        List<Map<String, Object>> rows;
        if (includeSubdirs) {
            if (parent == 0) {
                String sql = "SELECT node_type, name FROM file_node WHERE space_id=? AND deleted_at IS NULL"
                        + (hidden ? "" : " AND hidden=FALSE");
                rows = db.queryForList(sql, space);
            } else {
                String sql = "SELECT n.node_type, n.name FROM file_node_closure c "
                        + "JOIN file_node n ON n.id=c.descendant_id "
                        + "WHERE c.space_id=? AND c.ancestor_id=? AND c.depth > 0 AND n.deleted_at IS NULL"
                        + (hidden ? "" : " AND n.hidden=FALSE");
                rows = db.queryForList(sql, space, parent);
            }
        } else {
            String sql = "SELECT node_type, name FROM file_node WHERE space_id=? AND parent_id=? AND deleted_at IS NULL"
                    + (hidden ? "" : " AND hidden=FALSE");
            rows = db.queryForList(sql, space, parent);
        }
        long folders = 0, files = 0;
        Map<String, Long> categories = new LinkedHashMap<>();
        for (var row : rows) {
            if ("FOLDER".equals(row.get("node_type"))) {
                folders++;
            } else {
                files++;
                String cat = categorizeFile(String.valueOf(row.get("name")));
                categories.merge(cat, 1L, Long::sum);
            }
        }
        return new DirectoryStats(folders, files, categories);
    }
    public static String categorizeFile(String name) {
        String ext = name == null || !name.contains(".") ? "" : name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico", "avif" -> "IMAGE";
            case "pdf" -> "PDF";
            case "mp4", "webm", "mov", "mkv", "avi", "m4v" -> "VIDEO";
            case "mp3", "wav", "ogg", "flac", "aac", "m4a" -> "AUDIO";
            case "doc", "docx" -> "WORD";
            case "xls", "xlsx", "csv" -> "EXCEL";
            case "ppt", "pptx" -> "PPT";
            case "txt", "md", "markdown", "java", "js", "ts", "tsx", "jsx", "vue", "json", "css", "scss", "html", "xml", "py", "go", "rs", "sql", "yaml", "yml", "sh", "log" -> "CODE";
            case "zip", "tar", "gz", "7z", "rar" -> "ARCHIVE";
            default -> "OTHER";
        };
    }
    public Page list(long user,long space,long parent,int page,int size,boolean folders,boolean trash,String query,String sort,boolean hidden,boolean favorites) {
        return list(user, space, parent, page, size, folders, trash, query, sort, hidden, favorites, null);
    }
    public Page list(long user,long space,long parent,int page,int size,boolean folders,boolean trash,String query,String sort,boolean hidden,boolean favorites,String category) {
        NodeAccess nodeAccess = resolveNodeAccess(user, space, parent, false, false);
        if (nodeAccess.sharedRootId() != null && (trash || favorites)) {
            throw BizException.notFound("空间不存在或无权访问");
        }
        page=Math.max(1,page); size=Math.max(1,Math.min(200,size));
        String where="WHERE n.space_id=? AND n.deleted_at IS "+(trash?"NOT NULL":"NULL");
        List<Object> args=new ArrayList<>(); args.add(space);
        if (nodeAccess.sharedRootId() != null) {
            where += " AND EXISTS(SELECT 1 FROM file_node_closure c WHERE c.space_id=n.space_id AND c.ancestor_id=? AND c.descendant_id=n.id)";
            args.add(nodeAccess.sharedRootId());
        }
        if(!trash && (query==null || query.isBlank()) && !favorites) { parent(space,parent); where+=" AND n.parent_id=?"; args.add(parent); }
        if(folders) where+=" AND n.node_type='FOLDER'";
        if(!hidden) where+=" AND n.hidden=FALSE";
        if(query!=null && !query.isBlank()) { where+=" AND LOCATE(LOWER(?),LOWER(n.name))>0"; args.add(query); }
        if(favorites) { where+=" AND EXISTS(SELECT 1 FROM file_favorite f WHERE f.file_id=n.id AND f.user_id=?)"; args.add(user); }
        String normalizedCategory = category == null || category.isBlank() ? null : category.trim().toUpperCase(Locale.ROOT);
        if (normalizedCategory != null) {
            where += " AND n.node_type='FILE'";
        }
        String order=switch(sort==null?"":sort) {case "updated"->"n.updated_at DESC"; case "size"->"size_bytes DESC"; default->"n.name ASC";};
        if (normalizedCategory != null) {
            List<Node> allFiles = db.query(NODE_SELECT + where + " ORDER BY " + order + ",n.id", NODE_MAPPER, args.toArray());
            List<Node> filtered = allFiles.stream().filter(n -> normalizedCategory.equals(categorizeFile(n.name()))).toList();
            long total = filtered.size();
            int from = Math.min((page - 1) * size, filtered.size());
            int to = Math.min(from + size, filtered.size());
            return new Page(filtered.subList(from, to), total, page, size);
        }
        long total=db.queryForObject("SELECT COUNT(*) FROM file_node n "+where,Long.class,args.toArray());
        args.add(size); args.add((long)(page-1)*size);
        return new Page(db.query(NODE_SELECT+where+" ORDER BY n.node_type DESC,"+order+",n.id LIMIT ? OFFSET ?",NODE_MAPPER,args.toArray()),total,page,size);
    }
    @Transactional
    public Node folder(long user,long space,NodeInput input) {
        resolveNodeAccess(user, space, input.parentId(), true, true); parent(space,input.parentId()); validName(input.name()); unique(space,input.parentId(),input.name(),0);
        if(ancestors(space,input.parentId()).size()>=policy.maxDirectoryDepth()) throw BizException.conflict("目录不能超过系统深度限制");
        long id=IdWorker.getId();
        db.update("INSERT INTO file_node(id,space_id,parent_id,node_type,name,created_by) VALUES (?,?,?,'FOLDER',?,?)",id,space,input.parentId(),input.name(),user);
        insertClosure(space, id, input.parentId());
        return getNode(space,id,false);
    }
    private void enqueueSearchEvent(String eventType, long spaceId, long nodeId, Long versionId) {
        db.update(
                "INSERT INTO search_outbox(id, event_type, space_id, node_id, version_id, status) VALUES (?, ?, ?, ?, ?, 'PENDING')",
                IdWorker.getId(), eventType, spaceId, nodeId, versionId);
    }

    @Transactional
    public Node change(long user,long space,long id,ChangeInput input) {
        access(user,space,true); Node n=getNode(space,id,false); parent(space,input.parentId()); validName(input.name());
        List<Node> path=ancestors(space,input.parentId());
        if(path.stream().anyMatch(a->a.id().equals(n.id()))) throw BizException.conflict("不能将目录移动到自身或子目录");
        boolean parentChanged = !n.parentId().equals(String.valueOf(input.parentId()));
        if(parentChanged && path.size()+subtreeDepth(space,id)>policy.maxDirectoryDepth()) throw BizException.conflict("移动后目录超过系统深度限制");
        unique(space,input.parentId(),input.name(),id);
        if(db.update("UPDATE file_node SET name=?,parent_id=?,revision=revision+1,updated_at=UTC_TIMESTAMP(3) WHERE id=? AND revision=?",input.name(),input.parentId(),id,input.revision())!=1)
            throw BizException.conflict("文件已被修改，请刷新重试");
        if (parentChanged) {
            moveClosureWithinSpace(space, id, input.parentId());
        }
        enqueueSearchEvent("UPSERT", space, id, null);
        return getNode(space,id,false);
    }
    @Transactional
    public Node transfer(long user, long sourceSpace, long id, TransferInput input) {
        long targetSpace = input.targetSpaceId();
        long targetParent = input.targetParentId();
        lockSpacesOrdered(user, sourceSpace, targetSpace);
        Node source = getNode(sourceSpace, id, false);
        parent(targetSpace, targetParent);
        List<Node> targetAncestors = ancestors(targetSpace, targetParent);
        if (sourceSpace == targetSpace && targetAncestors.stream().anyMatch(a -> a.id().equals(source.id()))) {
            throw BizException.conflict("不能将目录移动到自身或子目录");
        }
        int depth = subtreeDepth(sourceSpace, id);
        if (targetAncestors.size() + depth > policy.maxDirectoryDepth()) {
            throw BizException.conflict("目标目录层级超过系统深度限制");
        }
        if ("MOVE".equals(input.mode())) {
            if (source.revision() != input.revision()) {
                throw BizException.conflict("文件已被修改，请刷新重试");
            }
            if (sourceSpace == targetSpace) {
                return change(user, sourceSpace, id, new ChangeInput(source.name(), targetParent, input.revision()));
            }
            unique(targetSpace, targetParent, source.name(), 0);
            List<Long> subtreeIds = db.queryForList(
                    "SELECT c.descendant_id FROM file_node_closure c JOIN file_node n ON n.id=c.descendant_id "
                            + "WHERE c.space_id=? AND c.ancestor_id=? AND n.deleted_at IS NULL ORDER BY c.depth ASC",
                    Long.class, sourceSpace, id);
            // 先把子树内部闭包边复制到目标空间，再清理源空间旧闭包边，最后挂载目标祖先
            db.update("INSERT IGNORE INTO file_node_closure(space_id,ancestor_id,descendant_id,depth) "
                    + "SELECT ?, c.ancestor_id, c.descendant_id, c.depth FROM file_node_closure c "
                    + "JOIN file_node_closure a ON a.space_id=? AND a.ancestor_id=? AND a.descendant_id=c.ancestor_id "
                    + "JOIN file_node_closure d ON d.space_id=? AND d.ancestor_id=? AND d.descendant_id=c.descendant_id "
                    + "JOIN file_node n ON n.id=c.descendant_id AND n.deleted_at IS NULL "
                    + "WHERE c.space_id=?",
                    targetSpace, sourceSpace, id, sourceSpace, id, sourceSpace);
            for (long nodeId : subtreeIds) {
                db.update("DELETE FROM file_node_closure WHERE space_id=? AND (ancestor_id=? OR descendant_id=?)",
                        sourceSpace, nodeId, nodeId);
                db.update("UPDATE file_node SET space_id=?, updated_at=UTC_TIMESTAMP(3) WHERE id=?", targetSpace, nodeId);
            }
            db.update("UPDATE file_node SET parent_id=?, revision=revision+1, updated_at=UTC_TIMESTAMP(3) WHERE id=?",
                    targetParent, id);
            if (targetParent > 0) {
                db.update("INSERT IGNORE INTO file_node_closure(space_id,ancestor_id,descendant_id,depth) "
                        + "SELECT ?, a.ancestor_id, d.descendant_id, a.depth + d.depth + 1 "
                        + "FROM file_node_closure a JOIN file_node_closure d ON a.space_id=? AND d.space_id=? "
                        + "WHERE a.descendant_id=? AND d.ancestor_id=?",
                        targetSpace, targetSpace, targetSpace, targetParent, id);
            }
            return getNode(targetSpace, id, false);
        }
        // COPY 模式：深拷贝子树节点并复用不可变物理对象（blob_object）
        unique(targetSpace, targetParent, source.name(), 0);
        long copiedRootId = copySubtree(user, sourceSpace, targetSpace, id, targetParent);
        return getNode(targetSpace, copiedRootId, false);
    }
    private long copySubtree(long user, long sourceSpace, long targetSpace, long sourceId, long targetParentId) {
        var subtreeRows = db.queryForList(
                "SELECT n.id, n.parent_id, n.node_type, n.name, n.hidden, n.current_version_id, c.depth "
                        + "FROM file_node_closure c JOIN file_node n ON n.id=c.descendant_id "
                        + "WHERE c.space_id=? AND c.ancestor_id=? AND n.deleted_at IS NULL ORDER BY c.depth ASC, n.id ASC",
                sourceSpace, sourceId);
        Map<Long, Long> idMap = new HashMap<>();
        for (var row : subtreeRows) {
            long oldId = ((Number) row.get("id")).longValue();
            long oldParent = ((Number) row.get("parent_id")).longValue();
            long newId = IdWorker.getId();
            long newParent = oldId == sourceId ? targetParentId : idMap.get(oldParent);
            idMap.put(oldId, newId);

            String nodeType = String.valueOf(row.get("node_type"));
            String name = String.valueOf(row.get("name"));
            boolean hidden = Boolean.TRUE.equals(row.get("hidden")) || Integer.valueOf(1).equals(row.get("hidden"));
            Long newVersionId = null;

            if ("FILE".equals(nodeType) && row.get("current_version_id") != null) {
                long oldVerId = ((Number) row.get("current_version_id")).longValue();
                var ver = db.queryForMap("SELECT blob_id, mime_type FROM file_version WHERE id=?", oldVerId);
                newVersionId = IdWorker.getId();
                db.update("INSERT INTO file_version(id,file_id,blob_id,version_number,mime_type,created_by) VALUES (?,?,?,1,?,?)",
                        newVersionId, newId, ver.get("blob_id"), ver.get("mime_type"), user);
            }
            db.update("INSERT INTO file_node(id,space_id,parent_id,node_type,name,current_version_id,hidden,created_by) VALUES (?,?,?,?,?,?,?,?)",
                    newId, targetSpace, newParent, nodeType, name, newVersionId, hidden, user);
            insertClosure(targetSpace, newId, newParent);
        }
        return idMap.get(sourceId);
    }
    private int subtreeDepth(long space,long id) {
        return db.queryForObject("WITH RECURSIVE tree AS (SELECT id,1 depth FROM file_node WHERE id=? AND space_id=? UNION ALL SELECT n.id,t.depth+1 FROM file_node n JOIN tree t ON n.parent_id=t.id WHERE n.space_id=? AND n.deleted_at IS NULL AND t.depth<257) SELECT COALESCE(MAX(depth),1) FROM tree",Integer.class,id,space,space);
    }
    @Transactional
    public void trash(long user,long space,long id,long revision) {
        access(user,space,true); Node n=getNode(space,id,false);
        if(n.revision()!=revision) throw BizException.conflict("文件已被修改，请刷新重试");
        // A recursive result is materialized before changing the tree, preserving earlier trash batches.
        var ids=db.queryForList("WITH RECURSIVE tree AS (SELECT id FROM file_node WHERE id=? AND space_id=? AND deleted_at IS NULL UNION ALL SELECT n.id FROM file_node n JOIN tree t ON n.parent_id=t.id WHERE n.space_id=? AND n.deleted_at IS NULL) SELECT id FROM tree",Long.class,id,space,space);
        for(long child:ids) db.update("UPDATE file_node SET deleted_at=UTC_TIMESTAMP(3),deleted_batch=?,revision=revision+1 WHERE id=?",id,child);
        db.update("DELETE f FROM file_favorite f JOIN file_node n ON n.id=f.file_id WHERE n.space_id=? AND n.deleted_at IS NOT NULL",space);
        for (long targetId : ids) {
            enqueueSearchEvent("DELETE", space, targetId, null);
        }
    }
    @Transactional
    public void restore(long user,long space,long id) {
        access(user,space,true); Node n=getNode(space,id,true);
        long batch=db.queryForObject("SELECT deleted_batch FROM file_node WHERE id=?",Long.class,id);
        if(batch!=id) throw BizException.conflict("请先恢复被删除的上级文件夹");
        parent(space,Long.parseLong(n.parentId()));
        var nodes=db.query("SELECT id,parent_id,name FROM file_node WHERE space_id=? AND deleted_batch=? AND deleted_at IS NOT NULL",(r,i)->new String[]{r.getString(1),r.getString(2),r.getString(3)},space,id);
        for(var item:nodes) unique(space,Long.parseLong(item[1]),item[2],Long.parseLong(item[0]));
        db.update("UPDATE file_node SET deleted_at=NULL,deleted_batch=0,revision=revision+1,updated_at=UTC_TIMESTAMP(3) WHERE space_id=? AND deleted_batch=?",space,id);
        for (var item : nodes) {
            enqueueSearchEvent("UPSERT", space, Long.parseLong(item[0]), null);
        }
    }
    @Transactional
    public int purgeTrashNode(long user, long space, long id) {
        access(user, space, true, true);
        getNode(space, id, true);
        var ids = db.queryForList(
                "WITH RECURSIVE tree AS (SELECT id FROM file_node WHERE id=? AND space_id=? AND deleted_at IS NOT NULL "
                        + "UNION ALL SELECT n.id FROM file_node n JOIN tree t ON n.parent_id=t.id WHERE n.space_id=? AND n.deleted_at IS NOT NULL) "
                        + "SELECT id FROM tree",
                Long.class, id, space, space);
        int removed = 0;
        for (long targetId : ids) {
            db.update("DELETE FROM file_node_closure WHERE space_id=? AND (ancestor_id=? OR descendant_id=?)", space, targetId, targetId);
            db.update("DELETE FROM search_text_chunk WHERE node_id=?", targetId);
            db.update("DELETE FROM user_quick_access WHERE node_id=?", targetId);
            db.update("DELETE FROM file_version WHERE file_id=?", targetId);
            removed += db.update("DELETE FROM file_node WHERE id=? AND space_id=? AND deleted_at IS NOT NULL", targetId, space);
        }
        return removed;
    }
    @Transactional
    public int emptyTrash(long user, long space) {
        access(user, space, true, true);
        var ids = db.queryForList("SELECT id FROM file_node WHERE space_id=? AND deleted_at IS NOT NULL", Long.class, space);
        int removed = 0;
        for (long targetId : ids) {
            db.update("DELETE FROM file_node_closure WHERE space_id=? AND (ancestor_id=? OR descendant_id=?)", space, targetId, targetId);
            db.update("DELETE FROM search_text_chunk WHERE node_id=?", targetId);
            db.update("DELETE FROM user_quick_access WHERE node_id=?", targetId);
            db.update("DELETE FROM file_version WHERE file_id=?", targetId);
            removed += db.update("DELETE FROM file_node WHERE id=? AND space_id=? AND deleted_at IS NOT NULL", targetId, space);
        }
        return removed;
    }
    @Transactional
    public Node upload(long user,long space,long parent,String name,String mime,InputStream input,long length,Long replace,Long expectedRevision) throws IOException {
        return upload(user, space, parent, name, mime, input, length, replace, expectedRevision, null, null);
    }
    @Transactional
    public Node upload(long user,long space,long parent,String name,String mime,InputStream input,long length,Long replace,Long expectedRevision,String expectedSha256) throws IOException {
        return upload(user, space, parent, name, mime, input, length, replace, expectedRevision, expectedSha256, null);
    }
    @Transactional
    public Node upload(long user,long space,long parent,String name,String mime,InputStream input,long length,Long replace,Long expectedRevision,String expectedSha256,Long targetProfileId) throws IOException {
        resolveNodeAccess(user, space, parent, true, true); parent(space,parent); validName(name); checkUploadPolicy(name, length); checkQuota(user, length);
        long id=replace==null?IdWorker.getId():replace;
        if(replace==null) unique(space,parent,name,0);
        else {
            Node current=getNode(space,id,false);
            if(!current.nodeType().equals("FILE") || expectedRevision==null || current.revision()!=expectedRevision) throw BizException.conflict("文件版本已变更");
        }
        long profileId = targetProfileId != null ? targetProfileId : storageRouter.resolveProfileIdForUsage(
                com.wanyue.cloud.modules.drive.storage.StorageRouterService.USAGE_USER_FILES);
        long maxBytes = Math.min(length, policy.uploadMaxFileBytes());
        var blob = storageRouter.write(profileId, input, maxBytes);
        if(blob.size()!=length) { storageRouter.delete(profileId, blob.key()); throw new IOException("文件长度不一致"); }
        if(expectedSha256 != null && !expectedSha256.isBlank() && !blob.sha256().equalsIgnoreCase(expectedSha256.strip())) {
            storageRouter.delete(profileId, blob.key());
            throw BizException.conflict("文件 SHA-256 校验失败，内容可能已损坏");
        }
        final long committedProfileId = profileId;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if(status!=STATUS_COMMITTED) try { storageRouter.delete(committedProfileId, blob.key()); } catch(IOException e) { /* Orphan scan can retry. */ }
            }
        });
        long blobId=IdWorker.getId(),version=IdWorker.getId();
        db.update("INSERT INTO blob_object(id,storage_profile_id,storage_key,size_bytes,sha256) VALUES (?,?,?,?,?)",blobId,profileId,blob.key(),blob.size(),blob.sha256());
        int number=db.queryForObject("SELECT COALESCE(MAX(version_number),0)+1 FROM file_version WHERE file_id=?",Integer.class,id);
        db.update("INSERT INTO file_version(id,file_id,blob_id,version_number,mime_type,created_by) VALUES (?,?,?,?,?,?)",version,id,blobId,number,safeMime(name),user);
        if(replace==null) {
            db.update("INSERT INTO file_node(id,space_id,parent_id,node_type,name,current_version_id,created_by) VALUES (?,?,?,'FILE',?,?,?)",id,space,parent,name,version,user);
            insertClosure(space, id, parent);
        } else {
            db.update("UPDATE file_node SET current_version_id=?,revision=revision+1,updated_at=UTC_TIMESTAMP(3) WHERE id=?",version,id);
        }
        pruneVersionsForFile(user, id, version);
        enqueueSearchEvent("UPSERT", space, id, version);
        return getNode(space,id,false);
    }
    public int getSystemMaxVersions() {
        var rows = db.queryForList("SELECT policy_value FROM sys_policy WHERE policy_key='max_file_versions'");
        if (rows.isEmpty()) return 20;
        try { return Math.max(1, Math.min(200, Integer.parseInt(String.valueOf(rows.get(0).get("policy_value"))))); }
        catch (Exception e) { return 20; }
    }
    public int getEffectiveMaxVersions(long user) {
        int sysMax = getSystemMaxVersions();
        var rows = db.queryForList("SELECT max_versions FROM user_version_policy WHERE user_id=?", user);
        if (rows.isEmpty()) return Math.min(10, sysMax);
        int userMax = ((Number) rows.get(0).get("max_versions")).intValue();
        return Math.max(1, Math.min(sysMax, userMax));
    }
    private void pruneVersionsForFile(long user, long fileId, long keepVersionId) {
        int limit = getEffectiveMaxVersions(user);
        List<Long> allIds = db.queryForList(
                "SELECT id FROM file_version WHERE file_id=? ORDER BY version_number DESC", Long.class, fileId);
        if (allIds.size() <= limit) return;
        for (int i = limit; i < allIds.size(); i++) {
            long vid = allIds.get(i);
            if (vid != keepVersionId) {
                db.update("DELETE FROM file_version WHERE id=?", vid);
            }
        }
    }
    public static String safeMime(String name) {
        String extension=name.toLowerCase(Locale.ROOT).replaceFirst("^.*\\.","");
        return switch(extension) {
            case "png"->"image/png"; case "jpg","jpeg"->"image/jpeg"; case "gif"->"image/gif"; case "webp"->"image/webp";
            case "pdf"->"application/pdf"; case "txt","md","java","js","ts","json","css","xml","csv","py","log","yaml","yml","html","svg"->"text/plain";
            case "mp3"->"audio/mpeg"; case "wav"->"audio/wav"; case "ogg"->"audio/ogg"; case "mp4"->"video/mp4"; case "webm"->"video/webm";
            default->"application/octet-stream";
        };
    }
    public Content content(long user,long space,long id,Long versionId) {
        resolveNodeAccess(user, space, id, false, false); Node n=getNode(space,id,false); ancestors(space,id);
        if(!n.nodeType().equals("FILE")) throw BizException.notFound("文件不存在");
        long version=versionId==null?Long.parseLong(n.currentVersionId()):versionId;
        var rows=db.query("SELECT b.storage_profile_id,b.storage_key,b.size_bytes,v.mime_type,b.sha256 FROM file_version v JOIN blob_object b ON b.id=v.blob_id WHERE v.id=? AND v.file_id=?",
                (r,i)->new Content(storageRouter.resolveReadablePath(r.getLong(1), r.getString(2)),r.getLong(3),n.name(),r.getString(4),r.getString(5)),version,id);
        if(rows.isEmpty() || !java.nio.file.Files.isRegularFile(rows.get(0).path())) throw BizException.notFound("文件内容不存在");
        return rows.get(0);
    }
    public TextChunk readTextChunk(long user, long space, long id, Long versionId, long offset, int limit) throws IOException {
        Content c = content(user, space, id, versionId);
        long safeOffset = Math.max(0L, offset);
        int safeLimit = Math.max(1024, Math.min(512 * 1024, limit));
        if (safeOffset >= c.size()) {
            return new TextChunk("", safeOffset, 0, c.size(), false, false);
        }
        int toRead = (int) Math.min(safeLimit, c.size() - safeOffset);
        byte[] buf = new byte[toRead];
        int totalRead = 0;
        try (var channel = java.nio.file.Files.newByteChannel(c.path(), java.nio.file.StandardOpenOption.READ)) {
            channel.position(safeOffset);
            var bb = java.nio.ByteBuffer.wrap(buf);
            while (bb.hasRemaining()) {
                int n = channel.read(bb);
                if (n < 0) break;
                totalRead += n;
            }
        }
        for (int i = 0; i < Math.min(totalRead, 4096); i++) {
            if (buf[i] == 0) {
                return new TextChunk("", safeOffset, 0, c.size(), false, true);
            }
        }
        int validLen = totalRead;
        if (safeOffset + totalRead < c.size() && totalRead > 0) {
            validLen = trimIncompleteUtf8Tail(buf, totalRead);
        }
        String text = new String(buf, 0, validLen, java.nio.charset.StandardCharsets.UTF_8);
        boolean hasMore = safeOffset + validLen < c.size();
        return new TextChunk(text, safeOffset, validLen, c.size(), hasMore, false);
    }
    private static int trimIncompleteUtf8Tail(byte[] buf, int len) {
        int i = len - 1;
        int minStart = Math.max(0, len - 4);
        while (i >= minStart && (buf[i] & 0xC0) == 0x80) {
            i--;
        }
        if (i < minStart) return len;
        int lead = buf[i] & 0xFF;
        int expected;
        if ((lead & 0x80) == 0) expected = 1;
        else if ((lead & 0xE0) == 0xC0) expected = 2;
        else if ((lead & 0xF0) == 0xE0) expected = 3;
        else if ((lead & 0xF8) == 0xF0) expected = 4;
        else return len;
        return (len - i < expected) ? i : len;
    }
    public List<Version> versions(long user,long space,long id) {
        resolveNodeAccess(user, space, id, false, false); getNode(space,id,false);
        return db.query("SELECT v.*,b.size_bytes,COALESCE(u.display_name,'') created_by_name FROM file_version v "
                        + "JOIN blob_object b ON b.id=v.blob_id LEFT JOIN sys_user u ON u.id=v.created_by "
                        + "WHERE v.file_id=? ORDER BY v.version_number DESC",
                (r,i)->new Version(r.getString("id"),r.getInt("version_number"),r.getLong("size_bytes"),
                        r.getString("mime_type"),
                        r.getObject("created_at",java.time.LocalDateTime.class).toInstant(java.time.ZoneOffset.UTC).toString(),
                        r.getString("created_by_name")),id);
    }
    @Transactional
    public Node restoreVersion(long user,long space,long id,long version,long revision) {
        access(user,space,true); Node n=getNode(space,id,false);
        if(n.revision()!=revision) throw BizException.conflict("文件版本已变更");
        var rows=db.queryForList("SELECT blob_id,mime_type FROM file_version WHERE id=? AND file_id=?",version,id);
        if(rows.isEmpty()) throw BizException.notFound("版本不存在");
        long next=IdWorker.getId();
        int number=db.queryForObject("SELECT MAX(version_number)+1 FROM file_version WHERE file_id=?",Integer.class,id);
        db.update("INSERT INTO file_version(id,file_id,blob_id,mime_type,version_number,created_by) VALUES (?,?,?,?,?,?)",next,id,rows.get(0).get("blob_id"),rows.get(0).get("mime_type"),number,user);
        db.update("UPDATE file_node SET current_version_id=?,revision=revision+1,updated_at=UTC_TIMESTAMP(3) WHERE id=?",next,id);
        return getNode(space,id,false);
    }
    @Transactional
    public void setHidden(long user, long space, long id, boolean enabled) {
        if (!enabled) requireVaultSession(user); // 取消私密只能在已解锁的私密设置中进行
        access(user, space, true, true);
        getNode(space, id, false);
        db.update("UPDATE file_node SET hidden=?, revision=revision+1, updated_at=UTC_TIMESTAMP(3) WHERE space_id=? AND id=? AND deleted_at IS NULL",
                enabled ? 1 : 0, space, id);
        if (enabled) {
            db.update("DELETE FROM file_favorite WHERE file_id=?", id);
            db.update("DELETE FROM user_quick_access WHERE node_id=?", id);
            db.update("UPDATE share_link SET disabled_at=UTC_TIMESTAMP(3) WHERE space_id=? AND node_id=? AND disabled_at IS NULL", space, id);
            db.update("UPDATE resource_share SET revoked_at=UTC_TIMESTAMP(3) WHERE space_id=? AND node_id=? AND revoked_at IS NULL", space, id);
            enqueueSearchEvent("DELETE", space, id, null);
        } else {
            enqueueSearchEvent("UPSERT", space, id, null);
        }
    }

    @Transactional
    public void favorite(long user,long space,long id,boolean enabled) {
        access(user,space,true); Node n=getNode(space,id,false);
        if(n.hidden()) throw BizException.conflict("隐藏文件不能收藏");
        if(enabled) db.update("INSERT IGNORE INTO file_favorite(user_id,file_id) VALUES (?,?)",user,id);
        else db.update("DELETE FROM file_favorite WHERE user_id=? AND file_id=?",user,id);
    }
}
