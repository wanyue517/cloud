package com.wanyue.cloud.modules.auth.controller;

import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.modules.auth.service.OauthAccountService;
import com.wanyue.cloud.modules.auth.vo.OauthAccountCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.OauthAccountUpdateReqVO;
import com.wanyue.cloud.modules.auth.vo.OauthAccountPageReqVO;
import com.wanyue.cloud.modules.auth.vo.OauthAccountRespVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 第三方OAuth账号绑定表单表基础 CRUD。
 * 注意：认证表与用户主表属于敏感业务，通用 CRUD 一律禁止外部直接调用。
 * 其他业务的通用 CRUD 也必须由管理员权限保护。
 */
@Validated
@RestController
@PreAuthorize("denyAll()")
@RequestMapping("/api/admin/auth/oauth-account")
public class OauthAccountController {

    @Resource
    private OauthAccountService oauthAccountService;

    @PostMapping
    public R<Long> create(@Valid @RequestBody OauthAccountCreateReqVO request) {
        return R.ok(oauthAccountService.create(request));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id,
                          @Valid @RequestBody OauthAccountUpdateReqVO request) {
        oauthAccountService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        oauthAccountService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<OauthAccountRespVO> get(@PathVariable Long id) {
        return R.ok(oauthAccountService.get(id));
    }

    @GetMapping("/page")
    public R<PageResult<OauthAccountRespVO>> page(@Valid OauthAccountPageReqVO request) {
        return R.ok(oauthAccountService.page(request));
    }
}

