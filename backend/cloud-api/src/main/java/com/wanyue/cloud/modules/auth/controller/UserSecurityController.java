package com.wanyue.cloud.modules.auth.controller;

import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.modules.auth.service.UserSecurityService;
import com.wanyue.cloud.modules.auth.vo.UserSecurityCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.UserSecurityUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.UserSecurityPageReqVO;
import com.wanyue.cloud.modules.auth.vo.UserSecurityRespVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户登录安全状态表单表基础 CRUD。
 * 注意：认证表与用户主表属于敏感业务，通用 CRUD 一律禁止外部直接调用。
 * 其他业务的通用 CRUD 也必须由管理员权限保护。
 */
@Validated
@RestController
@PreAuthorize("denyAll()")
@RequestMapping("/api/admin/auth/user-security")
public class UserSecurityController {

    @Resource
    private UserSecurityService userSecurityService;

    @PostMapping
    public R<Long> create(@Valid @RequestBody UserSecurityCreateReqVO request) {
        return R.ok(userSecurityService.create(request));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id,
                          @Valid @RequestBody UserSecurityUpdateReqVO request) {
        userSecurityService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userSecurityService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<UserSecurityRespVO> get(@PathVariable Long id) {
        return R.ok(userSecurityService.get(id));
    }

    @GetMapping("/page")
    public R<PageResult<UserSecurityRespVO>> page(@Valid UserSecurityPageReqVO request) {
        return R.ok(userSecurityService.page(request));
    }
}

