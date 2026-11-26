package com.wanyue.cloud.modules.auth.controller;

import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.modules.auth.service.RoleService;
import com.wanyue.cloud.modules.auth.vo.RoleCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.RoleUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.RolePageReqVO;
import com.wanyue.cloud.modules.auth.vo.RoleRespVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 系统级角色定义表；不用于空间资源ACL单表基础 CRUD。
 * 注意：认证表与用户主表属于敏感业务，通用 CRUD 一律禁止外部直接调用。
 * 其他业务的通用 CRUD 也必须由管理员权限保护。
 */
@Validated
@RestController
@PreAuthorize("denyAll()")
@RequestMapping("/api/admin/auth/role")
public class RoleController {

    @Resource
    private RoleService roleService;

    @PostMapping
    public R<Long> create(@Valid @RequestBody RoleCreateReqVO request) {
        return R.ok(roleService.create(request));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id,
                          @Valid @RequestBody RoleUpdateReqVO request) {
        roleService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<RoleRespVO> get(@PathVariable Long id) {
        return R.ok(roleService.get(id));
    }

    @GetMapping("/page")
    public R<PageResult<RoleRespVO>> page(@Valid RolePageReqVO request) {
        return R.ok(roleService.page(request));
    }
}

