package com.wanyue.cloud.modules.auth.controller;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.common.security.UserIdPrincipal;
import com.wanyue.cloud.modules.auth.service.QrLoginService;
import com.wanyue.cloud.modules.drive.AccountController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class QrLoginController {
    private final QrLoginService qrLoginService;

    public QrLoginController(QrLoginService qrLoginService) {
        this.qrLoginService = qrLoginService;
    }

    public record QrStatusResp(
            String status,
            String accessToken,
            Long expiresInSeconds,
            String displayName) {}

    @PostMapping("/api/admin/auth/qr/create")
    public R<QrLoginService.QrCreateResult> create() {
        return R.ok(qrLoginService.create());
    }

    @GetMapping("/api/admin/auth/qr/{qrId}/status")
    public R<QrStatusResp> status(
            @PathVariable String qrId,
            HttpServletRequest request,
            HttpServletResponse response) {
        QrLoginService.QrPollResult result = qrLoginService.pollStatus(
                qrId,
                request.getHeader("User-Agent"),
                request.getRemoteAddr());
        if ("CONFIRMED".equals(result.status()) && result.refreshToken() != null) {
            AccountController.cookie(response, result.refreshToken(), request.isSecure());
        }
        return R.ok(new QrStatusResp(
                result.status(),
                result.accessToken(),
                result.expiresInSeconds(),
                result.displayName()));
    }

    @PostMapping("/api/v1/auth/qr/{qrId}/scan")
    public R<Void> scan(Authentication auth, @PathVariable String qrId) {
        if (auth == null || !(auth.getPrincipal() instanceof UserIdPrincipal principal)) {
            throw new BizException(HttpStatus.UNAUTHORIZED, 401, "请先登录后再扫码");
        }
        qrLoginService.markScanned(principal.userId(), qrId);
        return R.ok();
    }

    @PostMapping("/api/v1/auth/qr/{qrId}/confirm")
    public R<Void> confirm(Authentication auth, @PathVariable String qrId) {
        if (auth == null || !(auth.getPrincipal() instanceof UserIdPrincipal principal)) {
            throw new BizException(HttpStatus.UNAUTHORIZED, 401, "请先登录后再确认授权");
        }
        qrLoginService.confirm(principal.userId(), qrId);
        return R.ok();
    }
}
