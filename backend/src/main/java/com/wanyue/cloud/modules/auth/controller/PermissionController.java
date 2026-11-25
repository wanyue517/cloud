package com.wanyue.cloud.modules.auth.controller;

import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.modules.auth.service.PermissionService;
import com.wanyue.cloud.modules.auth.vo.PermissionCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.PermissionUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.PermissionPageReqVO;
import com.wanyue.cloud.modules.auth.vo.PermissionRespVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 系统级权限定义表；空间资源权限另行建模单表基础 CRUD。
 * 注意：认证表与用户主表属于敏感业务，通用 CRUD 一律禁止外部直接调用。
 * 其他业务的通用 CRUD 也必须由管理员权限保护。
 */
@Validated
@RestController
@PreAuthorize("denyAll()")
@RequestMapping("/api/admin/auth/permission")
public class PermissionController {

    @Resource
    private PermissionService permissionService;

    @PostMapping
    public R<Long> create(@Valid @RequestBody PermissionCreateReqVO request) {
        return R.ok(permissionService.create(request));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id,
                          @Valid @RequestBody PermissionUpdateReqVO request) {
        permissionService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<PermissionRespVO> get(@PathVariable Long id) {
        return R.ok(permissionService.get(id));
    }

    @GetMapping("/page")
    public R<PageResult<PermissionRespVO>> page(@Valid PermissionPageReqVO request) {
        return R.ok(permissionService.page(request));
    }
}

