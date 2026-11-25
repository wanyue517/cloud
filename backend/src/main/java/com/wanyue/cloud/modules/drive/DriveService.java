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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import static com.wanyue.cloud.modules.drive.DriveModels.*;

@Service
public class DriveService {
    private final JdbcTemplate db;
    private final LocalStorage storage;
    public DriveService(JdbcTemplate db, LocalStorage storage) { this.db=db; this.storage=storage; }
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
    /** Lock the owning space for all tree mutations: prevents races with moves and deletes. */
    public void access(long user, long space, boolean lock) {
        var rows = db.queryForList("SELECT id FROM space WHERE id=? AND owner_user_id=? AND deleted=0 AND status='ACTIVE' AND kind='PERSONAL'" + (lock?" FOR UPDATE":""),space,user);
        if (rows.isEmpty()) throw BizException.notFound("空间不存在或无权访问");
    }
    public List<Space> spaces(long user) {
        return db.query("SELECT * FROM space WHERE owner_user_id=? AND deleted=0 AND status='ACTIVE' AND kind='PERSONAL' ORDER BY sort_order,id",
                (r,i)->new Space(r.getString("id"),r.getString("name"),r.getString("kind"),r.getString("color"),r.getLong("sort_order")),user);
    }
    @Transactional
    public String createSpace(long user, SpaceInput input) {
        long id=IdWorker.getId();
        db.update("INSERT INTO space(id,owner_user_id,kind,name,description,color,sort_order,created_by) VALUES (?,?,'PERSONAL',?,?,?,?,?)",
                id,user,validName(input.name()),input.description()==null?"":input.description(),input.color()==null?"#6366f1":input.color(),input.sortOrder()==null?0:input.sortOrder(),user);
        return String.valueOf(id);
    }
    @Transactional
    public void updateSpace(long user,long space,SpaceInput input) {
        access(user,space,true);
        db.update("UPDATE space SET name=?,description=?,color=?,sort_order=?,version=version+1 WHERE id=?",
                validName(input.name()),input.description()==null?"":input.description(),input.color()==null?"#6366f1":input.color(),input.sortOrder()==null?0:input.sortOrder(),space);
    }
    @Transactional
    public void deleteSpace(long user,long space,String confirmation) {
        access(user,space,true);
        String name=db.queryForObject("SELECT name FROM space WHERE id=?",String.class,space);
        if (!Objects.equals(name,confirmation)) throw new BizException(HttpStatus.BAD_REQUEST,400,"请输入空间名称确认删除");
        db.update("UPDATE space SET status='DELETED',deleted=id,delete_requested_at=UTC_TIMESTAMP(3),deleted_at=UTC_TIMESTAMP(3),purge_after=DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 30 DAY),access_version=access_version+1 WHERE id=?",space);
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
        while(node!=0) {
            if(!seen.add(node) || seen.size()>256) throw BizException.conflict("目录层级异常或超过256层");
            Node n=getNode(space,node,false); result.add(n); node=Long.parseLong(n.parentId());
        }
        Collections.reverse(result); return result;
    }
    private void unique(long space,long parent,String name,long except) {
        if(db.queryForObject("SELECT COUNT(*) FROM file_node WHERE space_id=? AND parent_id=? AND name=? AND deleted_at IS NULL AND id<>?",Long.class,space,parent,name,except)>0)
            throw BizException.conflict("此目录已有同名文件或文件夹");
    }
    public Page list(long user,long space,long parent,int page,int size,boolean folders,boolean trash,String query,String sort,boolean hidden,boolean favorites) {
        access(user,space,false);
        page=Math.max(1,page); size=Math.max(1,Math.min(200,size));
        String where="WHERE n.space_id=? AND n.deleted_at IS "+(trash?"NOT NULL":"NULL");
        List<Object> args=new ArrayList<>(); args.add(space);
        if(!trash && (query==null || query.isBlank()) && !favorites) { parent(space,parent); where+=" AND n.parent_id=?"; args.add(parent); }
        if(folders) where+=" AND n.node_type='FOLDER'";
        if(!hidden) where+=" AND n.hidden=FALSE";
        if(query!=null && !query.isBlank()) { where+=" AND LOCATE(LOWER(?),LOWER(n.name))>0"; args.add(query); }
        if(favorites) { where+=" AND EXISTS(SELECT 1 FROM file_favorite f WHERE f.file_id=n.id AND f.user_id=?)"; args.add(user); }
        long total=db.queryForObject("SELECT COUNT(*) FROM file_node n "+where,Long.class,args.toArray());
        String order=switch(sort==null?"":sort) {case "updated"->"n.updated_at DESC"; case "size"->"size_bytes DESC"; default->"n.name ASC";};
        args.add(size); args.add((long)(page-1)*size);
        return new Page(db.query(NODE_SELECT+where+" ORDER BY n.node_type DESC,"+order+",n.id LIMIT ? OFFSET ?",NODE_MAPPER,args.toArray()),total,page,size);
    }
    @Transactional
    public Node folder(long user,long space,NodeInput input) {
        access(user,space,true); parent(space,input.parentId()); validName(input.name()); unique(space,input.parentId(),input.name(),0);
        if(ancestors(space,input.parentId()).size()>=256) throw BizException.conflict("目录不能超过256层");
        long id=IdWorker.getId();
        db.update("INSERT INTO file_node(id,space_id,parent_id,node_type,name,created_by) VALUES (?,?,?,'FOLDER',?,?)",id,space,input.parentId(),input.name(),user);
        return getNode(space,id,false);
    }
    @Transactional
    public Node change(long user,long space,long id,ChangeInput input) {
        access(user,space,true); Node n=getNode(space,id,false); parent(space,input.parentId()); validName(input.name());
        List<Node> path=ancestors(space,input.parentId());
        if(path.stream().anyMatch(a->a.id().equals(n.id()))) throw BizException.conflict("不能将目录移动到自身或子目录");
        if(!n.parentId().equals(String.valueOf(input.parentId())) && path.size()+subtreeDepth(space,id)>256) throw BizException.conflict("移动后目录超过256层");
        unique(space,input.parentId(),input.name(),id);
        if(db.update("UPDATE file_node SET name=?,parent_id=?,revision=revision+1,updated_at=UTC_TIMESTAMP(3) WHERE id=? AND revision=?",input.name(),input.parentId(),id,input.revision())!=1)
            throw BizException.conflict("文件已被修改，请刷新重试");
        return getNode(space,id,false);
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
    }
    @Transactional
    public Node upload(long user,long space,long parent,String name,String mime,InputStream input,long length,Long replace,Long expectedRevision) throws IOException {
        access(user,space,true); parent(space,parent); validName(name);
        long id=replace==null?IdWorker.getId():replace;
        if(replace==null) unique(space,parent,name,0);
        else {
            Node current=getNode(space,id,false);
            if(!current.nodeType().equals("FILE") || expectedRevision==null || current.revision()!=expectedRevision) throw BizException.conflict("文件版本已变更");
        }
        LocalStorage.Stored blob=storage.write(input,Math.min(length,10L*1024*1024*1024));
        if(blob.size()!=length) { storage.delete(blob.key()); throw new IOException("文件长度不一致"); }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if(status!=STATUS_COMMITTED) try { storage.delete(blob.key()); } catch(IOException e) { /* Orphan scan can retry. */ }
            }
        });
        long blobId=IdWorker.getId(),version=IdWorker.getId();
        db.update("INSERT INTO blob_object(id,storage_profile_id,storage_key,size_bytes,sha256) VALUES (?,1,?,?,?)",blobId,blob.key(),blob.size(),blob.sha256());
        int number=db.queryForObject("SELECT COALESCE(MAX(version_number),0)+1 FROM file_version WHERE file_id=?",Integer.class,id);
        db.update("INSERT INTO file_version(id,file_id,blob_id,version_number,mime_type,created_by) VALUES (?,?,?,?,?,?)",version,id,blobId,number,safeMime(name),user);
        if(replace==null) db.update("INSERT INTO file_node(id,space_id,parent_id,node_type,name,current_version_id,created_by) VALUES (?,?,?,'FILE',?,?,?)",id,space,parent,name,version,user);
        else db.update("UPDATE file_node SET current_version_id=?,revision=revision+1,updated_at=UTC_TIMESTAMP(3) WHERE id=?",version,id);
        return getNode(space,id,false);
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
        access(user,space,false); Node n=getNode(space,id,false); ancestors(space,id);
        if(!n.nodeType().equals("FILE")) throw BizException.notFound("文件不存在");
        long version=versionId==null?Long.parseLong(n.currentVersionId()):versionId;
        var rows=db.query("SELECT b.storage_key,b.size_bytes,v.mime_type FROM file_version v JOIN blob_object b ON b.id=v.blob_id WHERE v.id=? AND v.file_id=?",
                (r,i)->new Content(storage.resolve(r.getString(1)),r.getLong(2),n.name(),r.getString(3)),version,id);
        if(rows.isEmpty() || !java.nio.file.Files.isRegularFile(rows.get(0).path())) throw BizException.notFound("文件内容不存在");
        return rows.get(0);
    }
    public List<Version> versions(long user,long space,long id) {
        access(user,space,false); getNode(space,id,false);
        return db.query("SELECT v.*,b.size_bytes FROM file_version v JOIN blob_object b ON b.id=v.blob_id WHERE v.file_id=? ORDER BY v.version_number DESC",
                (r,i)->new Version(r.getString("id"),r.getInt("version_number"),r.getLong("size_bytes"),r.getString("mime_type"),r.getObject("created_at",java.time.LocalDateTime.class).toInstant(java.time.ZoneOffset.UTC).toString()),id);
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
    public void favorite(long user,long space,long id,boolean enabled) {
        access(user,space,true); Node n=getNode(space,id,false);
        if(n.hidden()) throw BizException.conflict("隐藏文件不能收藏");
        if(enabled) db.update("INSERT IGNORE INTO file_favorite(user_id,file_id) VALUES (?,?)",user,id);
        else db.update("DELETE FROM file_favorite WHERE user_id=? AND file_id=?",user,id);
    }
}
