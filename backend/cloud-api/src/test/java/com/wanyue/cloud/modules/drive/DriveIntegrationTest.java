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
    @Autowired com.wanyue.cloud.modules.drive.storage.StorageRegistry storageRegistry;
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

    @Test void spaceMetadataPersistenceAndDirectoryCategoryStats() throws Exception {
        long userId = 1091L;
        long s1 = Long.parseLong(drive.createSpace(userId, new SpaceInput("设计空间", "设计素材", "#2563eb", 20L, "palette")));
        long s2 = Long.parseLong(drive.createSpace(userId, new SpaceInput("研发空间", "文档源码", "#16a34a", 10L, "code")));
        var listed = drive.spaces(userId);
        assertEquals(2, listed.size());
        assertEquals(String.valueOf(s2), listed.get(0).id());
        assertEquals("code", listed.get(0).icon());
        assertEquals("文档源码", listed.get(0).description());

        drive.updateSpace(userId, s1, new SpaceInput("设计空间V2", "更新描述", "#7c3aed", 5L, "sparkles"));
        var reordered = drive.spaces(userId);
        assertEquals(String.valueOf(s1), reordered.get(0).id());
        assertEquals("sparkles", reordered.get(0).icon());
        assertEquals("#7c3aed", reordered.get(0).color());
        assertEquals("更新描述", reordered.get(0).description());

        drive.folder(userId, s1, new NodeInput("子目录", 0));
        drive.upload(userId, s1, 0, "cover.png", "image/png", new ByteArrayInputStream("img".getBytes(StandardCharsets.UTF_8)), 3, null, null);
        drive.upload(userId, s1, 0, "spec.pdf", "application/pdf", new ByteArrayInputStream("pdf".getBytes(StandardCharsets.UTF_8)), 3, null, null);
        drive.upload(userId, s1, 0, "notes.md", "text/plain", new ByteArrayInputStream("md".getBytes(StandardCharsets.UTF_8)), 2, null, null);

        DirectoryStats stats = drive.stats(userId, s1, 0, false);
        assertEquals(1L, stats.folders());
        assertEquals(3L, stats.files());
        assertEquals(1L, stats.categories().get("IMAGE"));
        assertEquals(1L, stats.categories().get("PDF"));
        assertEquals(1L, stats.categories().get("CODE"));
        assertFalse(stats.categories().containsKey("VIDEO"));

        Page pdfOnly = drive.list(userId, s1, 0, 1, 50, false, false, null, "name", false, false, "PDF");
        assertEquals(1L, pdfOnly.total());
        assertEquals("spec.pdf", pdfOnly.items().get(0).name());
        Page imageWithQuery = drive.list(userId, s1, 0, 1, 50, false, false, "cov", "name", false, false, "IMAGE");
        assertEquals(1L, imageWithQuery.total());
        assertEquals("cover.png", imageWithQuery.items().get(0).name());
    }

    @Test void closureTableMaintainsRelationsAcrossCreatesAndMoves() {
        long userId = 1092L;
        long space = space(userId);
        Node a = drive.folder(userId, space, new NodeInput("A", 0));
        Node b = drive.folder(userId, space, new NodeInput("B", id(a)));
        Node c = drive.folder(userId, space, new NodeInput("C", id(b)));
        Node d = drive.folder(userId, space, new NodeInput("D", 0));

        Integer depthAC = db.queryForObject("SELECT depth FROM file_node_closure WHERE space_id=? AND ancestor_id=? AND descendant_id=?", Integer.class, space, id(a), id(c));
        assertEquals(2, depthAC);

        // Move subtree B (containing C) from A to D
        drive.change(userId, space, id(b), new ChangeInput("B", id(d), b.revision()));

        Integer staleAB = db.queryForObject("SELECT COUNT(*) FROM file_node_closure WHERE space_id=? AND ancestor_id=? AND descendant_id=?", Integer.class, space, id(a), id(b));
        Integer staleAC = db.queryForObject("SELECT COUNT(*) FROM file_node_closure WHERE space_id=? AND ancestor_id=? AND descendant_id=?", Integer.class, space, id(a), id(c));
        assertEquals(0, staleAB);
        assertEquals(0, staleAC);

        Integer depthDB = db.queryForObject("SELECT depth FROM file_node_closure WHERE space_id=? AND ancestor_id=? AND descendant_id=?", Integer.class, space, id(d), id(b));
        Integer depthDC = db.queryForObject("SELECT depth FROM file_node_closure WHERE space_id=? AND ancestor_id=? AND descendant_id=?", Integer.class, space, id(d), id(c));
        Integer depthBC = db.queryForObject("SELECT depth FROM file_node_closure WHERE space_id=? AND ancestor_id=? AND descendant_id=?", Integer.class, space, id(b), id(c));
        assertEquals(1, depthDB);
        assertEquals(2, depthDC);
        assertEquals(1, depthBC);
    }

    @Test void crossSpaceMoveAndCopyPreserveContentAndOwnership() throws Exception {
        long userId = 1093L;
        long otherUser = 1094L;
        long sourceSpace = space(userId);
        long targetSpace = space(userId);
        long foreignSpace = space(otherUser);

        Node folder = drive.folder(userId, sourceSpace, new NodeInput("项目包", 0));
        byte[] payload = "cross-space-content".getBytes(StandardCharsets.UTF_8);
        Node file = drive.upload(userId, sourceSpace, id(folder), "readme.txt", "text/plain", new ByteArrayInputStream(payload), payload.length, null, null);
        Node targetRoot = drive.folder(userId, targetSpace, new NodeInput("归档", 0));

        // 禁止跨用户空间传输
        assertThrows(BizException.class, () -> drive.transfer(userId, sourceSpace, id(folder), new TransferInput(foreignSpace, 0, "COPY", folder.revision())));

        // 跨空间复制：源空间保留，目标空间产生独立节点并可读取原内容
        Node copiedFolder = drive.transfer(userId, sourceSpace, id(folder), new TransferInput(targetSpace, id(targetRoot), "COPY", folder.revision()));
        assertNotEquals(folder.id(), copiedFolder.id());
        assertEquals(String.valueOf(targetSpace), copiedFolder.spaceId());
        var copiedChildren = drive.list(userId, targetSpace, id(copiedFolder), 1, 20, false, false, null, "name", false, false).items();
        assertEquals(1, copiedChildren.size());
        assertEquals("readme.txt", copiedChildren.get(0).name());
        assertEquals("cross-space-content", Files.readString(drive.content(userId, targetSpace, id(copiedChildren.get(0)), null).path()));
        // 原空间文件依然存在
        assertEquals("readme.txt", drive.getNode(sourceSpace, id(file), false).name());

        // 跨空间移动：重命名避开已复制的同名目录，验证整棵子树与闭包边完整迁移
        Node renamed = drive.change(userId, sourceSpace, id(folder), new ChangeInput("项目包-移动", 0, folder.revision()));
        Node movedFolder = drive.transfer(userId, sourceSpace, id(renamed), new TransferInput(targetSpace, id(targetRoot), "MOVE", renamed.revision()));
        assertEquals(renamed.id(), movedFolder.id());
        assertEquals(String.valueOf(targetSpace), movedFolder.spaceId());
        assertThrows(BizException.class, () -> drive.getNode(sourceSpace, id(renamed), false));
        Integer sourceClosureCount = db.queryForObject("SELECT COUNT(*) FROM file_node_closure WHERE space_id=? AND (ancestor_id=? OR descendant_id=?)", Integer.class, sourceSpace, id(renamed), id(file));
        assertEquals(0, sourceClosureCount);
        Integer targetDepth = db.queryForObject("SELECT depth FROM file_node_closure WHERE space_id=? AND ancestor_id=? AND descendant_id=?", Integer.class, targetSpace, id(targetRoot), id(file));
        assertEquals(2, targetDepth);
        assertEquals("cross-space-content", Files.readString(drive.content(userId, targetSpace, id(file), null).path()));
    }

    @Test void storageProviderSupportsPutGetRangeStatAndDelete() throws Exception {
        var provider = storageRegistry.defaultLocal();
        String key = "t057-" + java.util.UUID.randomUUID();
        byte[] content = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        try {
            var stored = provider.put(key, new ByteArrayInputStream(content), content.length);
            assertEquals(content.length, stored.size());
            assertTrue(provider.exists(key));
            assertEquals(content.length, provider.stat(key).size());
            try (var full = provider.get(key)) {
                assertArrayEquals(content, full.readAllBytes());
            }
            try (var range = provider.getRange(key, 4, 9)) {
                assertEquals("456789", new String(range.readAllBytes(), StandardCharsets.UTF_8));
            }
        } finally {
            provider.delete(key);
            assertFalse(provider.exists(key));
        }
    }

    @Test void chunkUploadValidatesSha256ReportsMissingAndCleansExpiredSessions() throws Exception {
        long userId = 1095L;
        long space = space(userId);
        byte[] payload = "chunk-integrity-check".getBytes(StandardCharsets.UTF_8);
        String validSha256 = java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(payload));

        Upload session = uploads.create(userId, new UploadInput(space, 0, "verified.txt", payload.length, "text/plain"));
        long sid = Long.parseLong(session.id());
        assertEquals(java.util.List.of(0), session.missing());
        assertTrue(session.received().isEmpty());

        // 错误分片 SHA-256 必须被拒绝
        assertThrows(BizException.class, () -> uploads.chunk(userId, sid, 0, new ByteArrayInputStream(payload), payload.length, "0".repeat(64)));
        assertEquals(java.util.List.of(0), uploads.status(userId, sid, false).missing());

        // 正确分片 SHA-256 落盘成功，missing 清空
        uploads.chunk(userId, sid, 0, new ByteArrayInputStream(payload), payload.length, validSha256);
        assertTrue(uploads.status(userId, sid, false).missing().isEmpty());

        // 错误整文件 SHA-256 禁止发布
        assertThrows(BizException.class, () -> uploads.complete(userId, sid, "f".repeat(64)));
        // 正确整文件 SHA-256 发布成功
        String fileId = uploads.complete(userId, sid, validSha256);
        assertEquals("verified.txt", drive.getNode(space, Long.parseLong(fileId), false).name());

        // 过期会话清理任务验证（T065）
        Upload expired = uploads.create(userId, new UploadInput(space, 0, "stale.txt", payload.length, "text/plain"));
        long eid = Long.parseLong(expired.id());
        uploads.chunk(userId, eid, 0, new ByteArrayInputStream(payload), payload.length, validSha256);
        db.update("UPDATE upload_session SET expires_at=DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 2 HOUR) WHERE id=?", eid);
        uploads.cleanExpired();
        assertEquals("EXPIRED", db.queryForObject("SELECT status FROM upload_session WHERE id=?", String.class, eid));
    }
}
