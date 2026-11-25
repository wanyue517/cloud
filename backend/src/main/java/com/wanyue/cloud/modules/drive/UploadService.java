package com.wanyue.cloud.modules.drive;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import static com.wanyue.cloud.modules.drive.DriveModels.*;

@Service
public class UploadService {
    private static final int CHUNK_SIZE=4*1024*1024;
    private final JdbcTemplate db;
    private final DriveService drive;
    private final Path temp;
    public UploadService(JdbcTemplate db,DriveService drive,@Value("${cloud.storage.upload-temp:data/uploads}") String temp) throws IOException {
        this.db=db;this.drive=drive;this.temp=Path.of(temp).toAbsolutePath().normalize(); Files.createDirectories(this.temp);
    }
    @Transactional
    public Upload create(long user,UploadInput input) throws IOException {
        drive.access(user,input.spaceId(),true); DriveService.validName(input.name());
        drive.ancestors(input.spaceId(),input.parentId());
        long active=db.queryForObject("SELECT COUNT(*) FROM upload_session WHERE user_id=? AND status='UPLOADING' AND expires_at>UTC_TIMESTAMP(3)",Long.class,user);
        if(active>=20) throw new BizException(HttpStatus.TOO_MANY_REQUESTS,429,"同时上传任务不能超过20个");
        long id=IdWorker.getId(); int count=(int)Math.max(1,(input.size()+CHUNK_SIZE-1)/CHUNK_SIZE);
        db.update("INSERT INTO upload_session(id,user_id,space_id,parent_id,name,size_bytes,chunk_size,chunk_count,mime_type,expires_at) VALUES (?,?,?,?,?,?,?,?,?,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY))",
                id,user,input.spaceId(),input.parentId(),input.name(),input.size(),CHUNK_SIZE,count,DriveService.safeMime(input.name()));
        return status(user,id,false);
    }
    private Map<String,Object> session(long user,long id,boolean lock) {
        var rows=db.queryForList("SELECT * FROM upload_session WHERE id=? AND user_id=? AND expires_at>UTC_TIMESTAMP(3)"+(lock?" FOR UPDATE":""),id,user);
        if(rows.isEmpty()) throw BizException.notFound("上传任务不存在或已过期");
        var s=rows.get(0);drive.access(user,number(s,"space_id"),false); return s;
    }
    private static long number(Map<String,Object>s,String key) { return ((Number)s.get(key)).longValue(); }
    public Upload status(long user,long id,boolean lock) throws IOException {
        var s=session(user,id,lock); int count=(int)number(s,"chunk_count");
        List<Integer> received=new ArrayList<>();
        for(int i=0;i<count;i++) if(Files.isRegularFile(part(id,i))) received.add(i);
        return new Upload(String.valueOf(id),CHUNK_SIZE,count,received,(String)s.get("status"),s.get("file_id")==null?null:s.get("file_id").toString());
    }
    private Path part(long id,int index) { return temp.resolve(id+"-"+index); }
    @Transactional(rollbackFor=Exception.class)
    public void chunk(long user,long id,int index,InputStream stream,long size) throws IOException {
        var s=session(user,id,true); int count=(int)number(s,"chunk_count");
        if(!s.get("status").equals("UPLOADING")) throw BizException.conflict("上传已结束");
        if(index<0 || index>=count) throw new BizException(HttpStatus.BAD_REQUEST,400,"分片编号无效");
        long expected=index==count-1?number(s,"size_bytes")-(long)index*CHUNK_SIZE:CHUNK_SIZE;
        if(size!=expected) throw new BizException(HttpStatus.BAD_REQUEST,400,"分片大小不正确");
        Path staging=Files.createTempFile(temp,"chunk-",".tmp");
        try {
            try(var out=Files.newOutputStream(staging)) {
                byte[] buffer=new byte[65536];long bytes=0;int n;
                while((n=stream.read(buffer))!=-1) { bytes+=n; if(bytes>expected) throw new IOException("分片超长"); out.write(buffer,0,n); }
                if(bytes!=expected) throw new IOException("分片不完整");
            }
            Files.move(staging,part(id,index),StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(staging); }
    }
    @Transactional(rollbackFor=Exception.class)
    public String complete(long user,long id) throws IOException {
        var s=session(user,id,true);
        if(s.get("status").equals("COMPLETED")) return s.get("file_id").toString();
        if(!s.get("status").equals("UPLOADING")) throw BizException.conflict("上传已取消");
        int count=(int)number(s,"chunk_count");
        for(int i=0;i<count;i++) if(!Files.isRegularFile(part(id,i))) throw BizException.conflict("仍有分片未上传");
        Node node;
        // Open only one chunk at a time, even for multi-gigabyte uploads.
        try(var combined=new InputStream() {
            private int index;
            private InputStream current;
            @Override public int read() throws IOException {
                byte[] one=new byte[1]; return read(one,0,1)==-1?-1:one[0]&255;
            }
            @Override public int read(byte[] buffer,int offset,int length) throws IOException {
                if(length==0) return 0;
                while(index<count) {
                    if(current==null) current=Files.newInputStream(part(id,index));
                    int read=current.read(buffer,offset,length);
                    if(read!=-1) return read;
                    current.close();current=null;index++;
                }
                return -1;
            }
            @Override public void close() throws IOException { if(current!=null) current.close(); }
        }) {
                node=drive.upload(user,number(s,"space_id"),number(s,"parent_id"),(String)s.get("name"),(String)s.get("mime_type"),combined,number(s,"size_bytes"),null,null);
        }
        db.update("UPDATE upload_session SET status='COMPLETED',file_id=? WHERE id=?",Long.parseLong(node.id()),id);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cleanup(id,count); }
        });
        return node.id();
    }
    @Transactional
    public void cancel(long user,long id) {
        var s=session(user,id,true);
        if(s.get("status").equals("COMPLETED")) throw BizException.conflict("上传已完成");
        db.update("UPDATE upload_session SET status='CANCELLED' WHERE id=?",id);
        cleanup(id,(int)number(s,"chunk_count"));
    }
    private boolean cleanup(long id,int count) {
        boolean success=true;
        for(int i=0;i<count;i++) try {Files.deleteIfExists(part(id,i));} catch(IOException e) { success=false; }
        return success;
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=3600000,initialDelay=3600000)
    @Transactional
    public void cleanExpired() {
        var expired=db.queryForList("SELECT id,chunk_count FROM upload_session WHERE expires_at<UTC_TIMESTAMP(3) AND status<>'EXPIRED' LIMIT 100 FOR UPDATE SKIP LOCKED");
        for(var session:expired) {
            long id=number(session,"id");
            if(cleanup(id,(int)number(session,"chunk_count"))) db.update("UPDATE upload_session SET status='EXPIRED' WHERE id=?",id);
        }
    }
}
