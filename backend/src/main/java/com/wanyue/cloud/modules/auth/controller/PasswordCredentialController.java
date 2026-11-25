package com.wanyue.cloud.modules.auth.controller;

import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.modules.auth.service.PasswordCredentialService;
import com.wanyue.cloud.modules.auth.vo.PasswordCredentialCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.PasswordCredentialUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.PasswordCredentialPageReqVO;
import com.wanyue.cloud.modules.auth.vo.PasswordCredentialRespVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户密码凭证表：每用户最多一份有效密码单表基础 CRUD。
 * 注意：认证表与用户主表属于敏感业务，通用 CRUD 一律禁止外部直接调用。
 * 其他业务的通用 CRUD 也必须由管理员权限保护。
 */
@Validated
@RestController
@PreAuthorize("denyAll()")
@RequestMapping("/api/admin/auth/password-credential")
public class PasswordCredentialController {

    @Resource
    private PasswordCredentialService passwordCredentialService;

    @PostMapping
    public R<Long> create(@Valid @RequestBody PasswordCredentialCreateReqVO request) {
        return R.ok(passwordCredentialService.create(request));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id,
                          @Valid @RequestBody PasswordCredentialUpdateReqVO request) {
        passwordCredentialService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        passwordCredentialService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<PasswordCredentialRespVO> get(@PathVariable Long id) {
        return R.ok(passwordCredentialService.get(id));
    }

    @GetMapping("/page")
    public R<PageResult<PasswordCredentialRespVO>> page(@Valid PasswordCredentialPageReqVO request) {
        return R.ok(passwordCredentialService.page(request));
    }
}

