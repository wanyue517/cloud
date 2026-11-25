package com.wanyue.cloud.modules.drive;

import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.common.exception.BizException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {
    private final JdbcTemplate db;
    public AdminController(JdbcTemplate db) { this.db=db; }
    @GetMapping("/stats") public R<Map<String,Object>> stats() {
        return R.ok(Map.of("users",db.queryForObject("SELECT COUNT(*) FROM sys_user WHERE deleted=0",Long.class),
                "spaces",db.queryForObject("SELECT COUNT(*) FROM space WHERE deleted=0",Long.class),
                "files",db.queryForObject("SELECT COUNT(*) FROM file_node WHERE node_type='FILE' AND deleted_at IS NULL",Long.class),
                "bytes",db.queryForObject("SELECT COALESCE(SUM(size_bytes),0) FROM blob_object",Long.class)));
    }
    @GetMapping("/users") public R<List<Map<String,Object>>> users(@RequestParam(defaultValue="1") int page) {
        int safe=Math.max(1,page);
        return R.ok(db.queryForList("SELECT CAST(id AS CHAR) id,display_name displayName,status,registered_at registeredAt FROM sys_user WHERE deleted=0 ORDER BY registered_at,id LIMIT 100 OFFSET ?",(long)(safe-1)*100));
    }
    public record Status(@Min(0) @Max(2) int status) {}
    @PutMapping("/users/{id}/status") @Transactional
    public R<Void> status(Authentication auth,@PathVariable long id,@Valid @RequestBody Status input) {
        if(id==DriveController.user(auth)) throw BizException.conflict("不能禁用当前账号");
        if(db.queryForObject("SELECT COUNT(*) FROM auth_user_role ur JOIN auth_role r ON r.id=ur.role_id WHERE ur.user_id=? AND ur.deleted=0 AND r.role_code='SUPER_ADMIN'",Long.class,id)>0)
            throw BizException.conflict("超级管理员状态不能在此修改");
        if(db.update("UPDATE sys_user SET status=? WHERE id=? AND deleted=0",input.status(),id)!=1) throw BizException.notFound("用户不存在");
        db.update("UPDATE auth_user_security SET session_version=session_version+1 WHERE user_id=? AND deleted=0",id);
        return R.ok();
    }
}
