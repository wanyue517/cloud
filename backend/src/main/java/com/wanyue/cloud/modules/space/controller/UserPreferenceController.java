package com.wanyue.cloud.modules.space.controller;

import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.modules.space.vo.UserPreferenceCreateReqVO;
import com.wanyue.cloud.modules.space.service.UserPreferenceService;
import com.wanyue.cloud.modules.space.vo.UserPreferenceUpdateReqVO;
import com.wanyue.cloud.modules.space.vo.UserPreferencePageReqVO;
import com.wanyue.cloud.modules.space.vo.UserPreferenceRespVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户空间导航偏好；默认入口仍存sys_user_setting单表基础 CRUD。
 * 注意：认证表与用户主表属于敏感业务，通用 CRUD 一律禁止外部直接调用。
 * 其他业务的通用 CRUD 也必须由管理员权限保护。
 */
@Validated
@RestController
@PreAuthorize("hasAuthority('system:generated:crud')")
@RequestMapping("/api/admin/space/user-preference")
public class UserPreferenceController {

    @Resource
    private UserPreferenceService userPreferenceService;

    @PostMapping
    public R<Long> create(@Valid @RequestBody UserPreferenceCreateReqVO request) {
        return R.ok(userPreferenceService.create(request));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id,
                          @Valid @RequestBody UserPreferenceUpdateReqVO request) {
        userPreferenceService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userPreferenceService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<UserPreferenceRespVO> get(@PathVariable Long id) {
        return R.ok(userPreferenceService.get(id));
    }

    @GetMapping("/page")
    public R<PageResult<UserPreferenceRespVO>> page(@Valid UserPreferencePageReqVO request) {
        return R.ok(userPreferenceService.page(request));
    }
}

