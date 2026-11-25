package com.wanyue.cloud.modules.drive;

import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.modules.auth.service.WebSessionService;
import com.wanyue.cloud.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1")
public class AccountController {
    private final WebSessionService sessions;
    private final JdbcTemplate db;
    public AccountController(WebSessionService sessions,JdbcTemplate db) { this.sessions=sessions;this.db=db; }
    public static void cookie(HttpServletResponse response,String token,boolean secure) {
        response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from("cloudlane.refresh",token).httpOnly(true).secure(secure).sameSite("Strict")
                .path("/api/v1/auth").maxAge(token.isEmpty()?Duration.ZERO:Duration.ofDays(30)).build().toString());
    }
    @PostMapping("/auth/refresh") public R<Map<String,Object>> refresh(@CookieValue(name="cloudlane.refresh",required=false) String token,
            @RequestHeader(name="X-Requested-With",required=false) String requestedWith,HttpServletRequest request,HttpServletResponse response) {
        if(!"Cloudlane".equals(requestedWith)) throw new BizException(HttpStatus.FORBIDDEN,403,"请求来源无效");
        var refreshed=sessions.refresh(token);cookie(response,refreshed.refreshToken(),request.isSecure());
        return R.ok(Map.of("accessToken",refreshed.accessToken(),"expiresIn",refreshed.expiresIn()));
    }
    @PostMapping("/auth/logout") public R<Void> logout(Authentication auth,HttpServletRequest request,HttpServletResponse response) {
        sessions.logout(DriveController.user(auth));cookie(response,"",request.isSecure());return R.ok();
    }
    @GetMapping("/me") public R<Map<String,Object>> me(Authentication auth) {
        var data=db.queryForMap("SELECT CAST(id AS CHAR) id,display_name displayName,avatar_url avatarUrl FROM sys_user WHERE id=? AND deleted=0",DriveController.user(auth));
        data.put("admin",auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_SUPER_ADMIN")));return R.ok(data);
    }
    public record ProfileInput(@NotBlank @Size(max=64) String displayName) {}
    @PutMapping("/me") public R<Void> updateMe(Authentication auth,@Valid @RequestBody ProfileInput input) {
        db.update("UPDATE sys_user SET display_name=?,updated_at=UTC_TIMESTAMP(3),version=version+1 WHERE id=? AND deleted=0",input.displayName().strip(),DriveController.user(auth));return R.ok();
    }
}
