package com.wanyue.cloud.modules.drive;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;
import static com.wanyue.cloud.modules.drive.DriveModels.*;

@SpringBootTest
@Transactional
class DriveIntegrationTest {
    @MockitoBean com.wanyue.cloud.common.config.RedisStartupCheck redisStartupCheck;
    @Autowired DriveService drive;
    @Autowired UploadService uploads;
    @Autowired JdbcTemplate db;
    private long space(long user) { return Long.parseLong(drive.createSpace(user,new SpaceInput("测试空间","","#6366f1",0L))); }
    private long id(Node n) { return Long.parseLong(n.id()); }
    @Test void treeConflictsCyclesAndOwnershipAreEnforced() {
        long space=space(1001);
        Node a=drive.folder(1001,space,new NodeInput("一级",0));
        Node b=drive.folder(1001,space,new NodeInput("二级",id(a)));
        assertEquals(2,drive.ancestors(space,id(b)).size());
        assertThrows(BizException.class,()->drive.change(1001,space,id(a),new ChangeInput("一级",id(b),0)));
        assertThrows(BizException.class,()->drive.folder(1001,space,new NodeInput("一级",0)));
        assertThrows(BizException.class,()->drive.list(1002,space,0,1,100,false,false,null,null,false,false));
        Node changed=drive.change(1001,space,id(b),new ChangeInput("已改名",0,0));
        assertEquals(1,changed.revision());
        assertThrows(BizException.class,()->drive.change(1001,space,id(b),new ChangeInput("过期请求",0,0)));
    }
    @Test void recursiveTrashRestoresOneBatchAndKeepsPreviouslyDeletedChildrenDeleted() {
        long space=space(1001);
        Node a=drive.folder(1001,space,new NodeInput("父目录",0));
        Node b=drive.folder(1001,space,new NodeInput("之前删除",id(a)));
        Node c=drive.folder(1001,space,new NodeInput("保留目录",id(a)));
        drive.trash(1001,space,id(b),0);
        drive.trash(1001,space,id(a),0);
        assertThrows(BizException.class,()->drive.getNode(space,id(c),false));
        drive.restore(1001,space,id(a));
        assertNotNull(drive.getNode(space,id(c),false));
        assertThrows(BizException.class,()->drive.getNode(space,id(b),false));
        drive.restore(1001,space,id(b));
        assertNotNull(drive.getNode(space,id(b),false));
    }
    @Test void versionsAreImmutableAndRestoreCreatesAnotherVersion() throws Exception {
        long space=space(1001);
        byte[] first="第一版".getBytes(StandardCharsets.UTF_8),second="第二版".getBytes(StandardCharsets.UTF_8);
        Node n=drive.upload(1001,space,0,"说明.md","text/plain",new ByteArrayInputStream(first),first.length,null,null);
        Node v2=drive.upload(1001,space,0,n.name(),"text/plain",new ByteArrayInputStream(second),second.length,id(n),0L);
        assertEquals(2,drive.versions(1001,space,id(n)).size());
        assertArrayEquals(first,Files.readAllBytes(drive.content(1001,space,id(n),Long.parseLong(n.currentVersionId())).path()));
        Node restored=drive.restoreVersion(1001,space,id(n),Long.parseLong(n.currentVersionId()),v2.revision());
        assertNotEquals(n.currentVersionId(),restored.currentVersionId());
        assertEquals(3,drive.versions(1001,space,id(n)).size());
        assertArrayEquals(first,Files.readAllBytes(drive.content(1001,space,id(n),null).path()));
        assertThrows(BizException.class,()->drive.content(1002,space,id(n),null));
    }
    @Test void chunksResumeAndCompletionIsIdempotent() throws Exception {
        long space=space(1001);
        byte[] bytes="完整文件内容".getBytes(StandardCharsets.UTF_8);
        Upload upload=uploads.create(1001,new UploadInput(space,0,"分片.txt",bytes.length,"text/plain"));
        long id=Long.parseLong(upload.id());
        assertThrows(BizException.class,()->uploads.complete(1001,id));
        assertThrows(BizException.class,()->uploads.status(1002,id,false));
        uploads.chunk(1001,id,0,new ByteArrayInputStream(bytes),bytes.length);
        assertEquals(java.util.List.of(0),uploads.status(1001,id,false).received());
        String file=uploads.complete(1001,id);
        assertEquals(file,uploads.complete(1001,id));
        assertEquals(1,drive.list(1001,space,0,1,100,false,false,null,null,false,false).total());
        assertArrayEquals(bytes,Files.readAllBytes(drive.content(1001,space,Long.parseLong(file),null).path()));
    }
    @Test void filenamesAreValidatedAndMarkupIsNeverServedAsExecutableHtml() {
        for(String name:new String[]{"../secret","a/b","bad\\name",".."," name","a\nname"}) assertThrows(BizException.class,()->DriveService.validName(name));
        assertEquals("text/plain",DriveService.safeMime("untrusted.html"));
        assertEquals("text/plain",DriveService.safeMime("untrusted.svg"));
    }
}
