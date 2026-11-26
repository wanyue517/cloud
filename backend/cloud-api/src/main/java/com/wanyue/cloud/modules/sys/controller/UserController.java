package com.wanyue.cloud.modules.sys.controller;

import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.modules.sys.vo.UserCreateReqVO;
import com.wanyue.cloud.modules.sys.service.UserService;
import com.wanyue.cloud.modules.sys.vo.UserUpdateReqVO;
import com.wanyue.cloud.modules.sys.vo.UserPageReqVO;
import com.wanyue.cloud.modules.sys.vo.UserRespVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 系统用户主表单表基础 CRUD。
 * 注意：认证表与用户主表属于敏感业务，通用 CRUD 一律禁止外部直接调用。
 * 其他业务的通用 CRUD 也必须由管理员权限保护。
 */
@Validated
@RestController
@PreAuthorize("denyAll()")
@RequestMapping("/api/admin/sys/user")
public class UserController {

    @Resource
    private UserService userService;

    @PostMapping
    public R<Long> create(@Valid @RequestBody UserCreateReqVO request) {
        return R.ok(userService.create(request));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id,
                          @Valid @RequestBody UserUpdateReqVO request) {
        userService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<UserRespVO> get(@PathVariable Long id) {
        return R.ok(userService.get(id));
    }

    @GetMapping("/page")
    public R<PageResult<UserRespVO>> page(@Valid UserPageReqVO request) {
        return R.ok(userService.page(request));
    }
}

