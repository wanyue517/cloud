package com.wanyue.cloud.modules.auth.controller;

import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.modules.auth.service.SessionService;
import com.wanyue.cloud.modules.auth.vo.SessionCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.SessionUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.SessionPageReqVO;
import com.wanyue.cloud.modules.auth.vo.SessionRespVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户登录会话与Refresh Token表单表基础 CRUD。
 * 注意：认证表与用户主表属于敏感业务，通用 CRUD 一律禁止外部直接调用。
 * 其他业务的通用 CRUD 也必须由管理员权限保护。
 */
@Validated
@RestController
@PreAuthorize("denyAll()")
@RequestMapping("/api/admin/auth/session")
public class SessionController {

    @Resource
    private SessionService sessionService;

    @PostMapping
    public R<Long> create(@Valid @RequestBody SessionCreateReqVO request) {
        return R.ok(sessionService.create(request));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id,
                          @Valid @RequestBody SessionUpdateReqVO request) {
        sessionService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        sessionService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<SessionRespVO> get(@PathVariable Long id) {
        return R.ok(sessionService.get(id));
    }

    @GetMapping("/page")
    public R<PageResult<SessionRespVO>> page(@Valid SessionPageReqVO request) {
        return R.ok(sessionService.page(request));
    }
}

