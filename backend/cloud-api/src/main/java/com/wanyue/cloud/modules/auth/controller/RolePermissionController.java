package com.wanyue.cloud.modules.auth.controller;

import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.modules.auth.service.RolePermissionService;
import com.wanyue.cloud.modules.auth.vo.RolePermissionCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.RolePermissionUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.RolePermissionPageReqVO;
import com.wanyue.cloud.modules.auth.vo.RolePermissionRespVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 系统角色与系统权限关联表单表基础 CRUD。
 * 注意：认证表与用户主表属于敏感业务，通用 CRUD 一律禁止外部直接调用。
 * 其他业务的通用 CRUD 也必须由管理员权限保护。
 */
@Validated
@RestController
@PreAuthorize("denyAll()")
@RequestMapping("/api/admin/auth/role-permission")
public class RolePermissionController {

    @Resource
    private RolePermissionService rolePermissionService;

    @PostMapping
    public R<Long> create(@Valid @RequestBody RolePermissionCreateReqVO request) {
        return R.ok(rolePermissionService.create(request));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id,
                          @Valid @RequestBody RolePermissionUpdateReqVO request) {
        rolePermissionService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        rolePermissionService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<RolePermissionRespVO> get(@PathVariable Long id) {
        return R.ok(rolePermissionService.get(id));
    }

    @GetMapping("/page")
    public R<PageResult<RolePermissionRespVO>> page(@Valid RolePermissionPageReqVO request) {
        return R.ok(rolePermissionService.page(request));
    }
}

