package com.wanyue.cloud.modules.auth.controller;

import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.modules.auth.service.RecoveryCodeService;
import com.wanyue.cloud.modules.auth.vo.RecoveryCodeCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.RecoveryCodeUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.RecoveryCodePageReqVO;
import com.wanyue.cloud.modules.auth.vo.RecoveryCodeRespVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 多因素认证一次性恢复码表单表基础 CRUD。
 * 注意：认证表与用户主表属于敏感业务，通用 CRUD 一律禁止外部直接调用。
 * 其他业务的通用 CRUD 也必须由管理员权限保护。
 */
@Validated
@RestController
@PreAuthorize("denyAll()")
@RequestMapping("/api/admin/auth/recovery-code")
public class RecoveryCodeController {

    @Resource
    private RecoveryCodeService recoveryCodeService;

    @PostMapping
    public R<Long> create(@Valid @RequestBody RecoveryCodeCreateReqVO request) {
        return R.ok(recoveryCodeService.create(request));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id,
                          @Valid @RequestBody RecoveryCodeUpdateReqVO request) {
        recoveryCodeService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        recoveryCodeService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<RecoveryCodeRespVO> get(@PathVariable Long id) {
        return R.ok(recoveryCodeService.get(id));
    }

    @GetMapping("/page")
    public R<PageResult<RecoveryCodeRespVO>> page(@Valid RecoveryCodePageReqVO request) {
        return R.ok(recoveryCodeService.page(request));
    }
}

