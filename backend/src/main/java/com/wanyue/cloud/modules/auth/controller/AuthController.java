package com.wanyue.cloud.modules.auth.controller;

import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.modules.auth.service.AuthService;
import com.wanyue.cloud.modules.auth.vo.EmailLoginReqVO;
import com.wanyue.cloud.modules.auth.vo.EmailRegisterReqVO;
import com.wanyue.cloud.modules.auth.vo.IdentityCreateReqVO;
import com.wanyue.cloud.modules.auth.vo.UserLoginRespVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import com.wanyue.cloud.modules.auth.service.VerificationRateLimiter;
import org.hibernate.validator.constraints.Length;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 认证入口。注册和验证码接口允许匿名访问，未完成的入口默认拒绝访问。
 */
@RestController
@PreAuthorize("denyAll()")
@RequestMapping("/api/admin/auth")
public class AuthController {

    @Resource
    private AuthService authService;

    @Resource
    private VerificationRateLimiter rateLimiter;
    @Resource
    private com.wanyue.cloud.modules.auth.service.WebSessionService webSessions;

    @PreAuthorize("permitAll()")
    @PostMapping("/login/email")
    public R<UserLoginRespVO> login(@Valid @RequestBody EmailLoginReqVO request,
            HttpServletRequest servletRequest,jakarta.servlet.http.HttpServletResponse response) {
        UserLoginRespVO result=authService.loginWithEmail(request);
        String refresh=webSessions.start(result.getUser().getId());
        com.wanyue.cloud.modules.drive.AccountController.cookie(response,refresh,servletRequest.isSecure());
        return R.ok(result);
    }

    @PreAuthorize("permitAll()")
    @PostMapping("/email")
    public R<Boolean> register(@Valid @RequestBody EmailRegisterReqVO request) {
        return R.ok(authService.registerWithEmail(request));
    }

    @PreAuthorize("permitAll()")
    @PostMapping("/register/send-email-code")
    public R<Boolean> sendEmailCode(
            @RequestParam
            @NotBlank(message = "邮箱不能为空")
            @Email(message = "邮箱格式不正确")
            @Length(max = 191, message = "邮箱长度不能超过191个字符")
            String email,
            HttpServletRequest request
    ) {

        rateLimiter.checkIp(request.getRemoteAddr());
        authService.sendEmailCode(email);
        return R.ok(true);
    }
}
