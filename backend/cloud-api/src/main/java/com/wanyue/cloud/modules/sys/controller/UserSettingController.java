package com.wanyue.cloud.modules.sys.controller;

import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.modules.sys.service.UserSettingService;
import com.wanyue.cloud.modules.sys.vo.UserSettingPageReqVO;
import com.wanyue.cloud.modules.sys.vo.UserSettingRespVO;
import com.wanyue.cloud.modules.sys.vo.UserSettingUpdateReqVO;
import com.wanyue.cloud.modules.sys.vo.UserSettingCreateReqVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户个性化设置表单表基础 CRUD。
 * 注意：认证表与用户主表属于敏感业务，通用 CRUD 一律禁止外部直接调用。
 * 其他业务的通用 CRUD 也必须由管理员权限保护。
 */
@Validated
@RestController
@PreAuthorize("hasAuthority('system:generated:crud')")
@RequestMapping("/api/admin/sys/user-setting")
public class UserSettingController {

    @Resource
    private UserSettingService userSettingService;

    @PostMapping
    public R<Long> create(@Valid @RequestBody UserSettingCreateReqVO request) {
        return R.ok(userSettingService.create(request));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id,
                          @Valid @RequestBody UserSettingUpdateReqVO request) {
        userSettingService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userSettingService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<UserSettingRespVO> get(@PathVariable Long id) {
        return R.ok(userSettingService.get(id));
    }

    @GetMapping("/page")
    public R<PageResult<UserSettingRespVO>> page(@Valid UserSettingPageReqVO request) {
        return R.ok(userSettingService.page(request));
    }
}

