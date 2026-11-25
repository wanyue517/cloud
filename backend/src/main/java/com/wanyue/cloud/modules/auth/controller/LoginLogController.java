package com.wanyue.cloud.modules.auth.controller;

import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.modules.auth.service.LoginLogService;
import com.wanyue.cloud.modules.auth.vo.LoginLogCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.LoginLogUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.LoginLogPageReqVO;
import com.wanyue.cloud.modules.auth.vo.LoginLogRespVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 登录审计日志：仅记录脱敏标识，不存凭据单表基础 CRUD。
 * 注意：认证表与用户主表属于敏感业务，通用 CRUD 一律禁止外部直接调用。
 * 其他业务的通用 CRUD 也必须由管理员权限保护。
 */
@Validated
@RestController
@PreAuthorize("denyAll()")
@RequestMapping("/api/admin/auth/login-log")
public class LoginLogController {

    @Resource
    private LoginLogService loginLogService;

    @PostMapping
    public R<Long> create(@Valid @RequestBody LoginLogCreateReqVO request) {
        return R.ok(loginLogService.create(request));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id,
                          @Valid @RequestBody LoginLogUpdateReqVO request) {
        loginLogService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        loginLogService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<LoginLogRespVO> get(@PathVariable Long id) {
        return R.ok(loginLogService.get(id));
    }

    @GetMapping("/page")
    public R<PageResult<LoginLogRespVO>> page(@Valid LoginLogPageReqVO request) {
        return R.ok(loginLogService.page(request));
    }
}

