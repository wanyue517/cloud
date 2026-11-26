package com.wanyue.cloud.modules.space.controller;

import com.wanyue.cloud.common.result.PageResult;
import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.modules.space.service.MemberService;
import com.wanyue.cloud.modules.space.vo.MemberCreateReqVO;
import com.wanyue.cloud.modules.space.vo.MemberPageReqVO;
import com.wanyue.cloud.modules.space.vo.MemberRespVO;
import com.wanyue.cloud.modules.space.vo.MemberUpdateReqVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 共享空间成员关系，独立于文件夹授权单表基础 CRUD。
 * 注意：认证表与用户主表属于敏感业务，通用 CRUD 一律禁止外部直接调用。
 * 其他业务的通用 CRUD 也必须由管理员权限保护。
 */
@Validated
@RestController
@PreAuthorize("hasAuthority('system:generated:crud')")
@RequestMapping("/api/admin/space/member")
public class MemberController {

    @Resource
    private MemberService memberService;

    @PostMapping
    public R<Long> create(@Valid @RequestBody MemberCreateReqVO request) {
        return R.ok(memberService.create(request));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id,
                          @Valid @RequestBody MemberUpdateReqVO request) {
        memberService.update(id, request);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        memberService.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<MemberRespVO> get(@PathVariable Long id) {
        return R.ok(memberService.get(id));
    }

    @GetMapping("/page")
    public R<PageResult<MemberRespVO>> page(@Valid MemberPageReqVO request) {
        return R.ok(memberService.page(request));
    }
}

