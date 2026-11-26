package com.wanyue.cloud.modules.auth.controller;

import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.modules.auth.service.SecurityLogService;
import com.wanyue.cloud.modules.auth.vo.SecurityLogCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.SecurityLogUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.SecurityLogPageReqVO;
import com.wanyue.cloud.modules.auth.vo.SecurityLogRespVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 账号安全事件审计日志单表基础 CRUD。
 * 注意：认证表与用户主表属于敏感业务，通用 CRUD 一律禁止外部直接调用。
 * 其他业务的通用 CRUD 也必须由管理员权限保护。
 */
@Validated
@RestController
@PreAuthorize("denyAll()")
@RequestMapping("/api/admin/auth/security-log")
public class SecurityLogController {

    @Resource
    private SecurityLogService securityLogService;

    @PostMapping
    public R<Long> create(@Valid @RequestBody SecurityLogCreateReqVO request) {
        return R.ok(securityLogService.create(request));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id,
                          @Valid @RequestBody SecurityLogUpdateReqVO request) {
        securityLogService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        securityLogService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<SecurityLogRespVO> get(@PathVariable Long id) {
        return R.ok(securityLogService.get(id));
    }

    @GetMapping("/page")
    public R<PageResult<SecurityLogRespVO>> page(@Valid SecurityLogPageReqVO request) {
        return R.ok(securityLogService.page(request));
    }
}

