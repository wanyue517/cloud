package com.wanyue.cloud.common.security;

import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class AccessTokenFilter extends OncePerRequestFilter {
    private final JwtTokenProvider tokens;
    private final JdbcTemplate jdbc;
    public AccessTokenFilter(JwtTokenProvider tokens, JdbcTemplate jdbc) {
        this.tokens = tokens;
        this.jdbc = jdbc;
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                var claims = tokens.verify(header.substring(7));
                long userId = Long.parseLong(claims.getSubject());
                var users = jdbc.queryForList("SELECT u.id FROM sys_user u JOIN auth_user_security s ON s.user_id=u.id "
                        + "WHERE u.id=? AND u.deleted=0 AND u.status=1 AND s.deleted=0 AND s.session_version=?",
                        userId, claims.get("sessionVersion", Integer.class));
                if (users.isEmpty()) throw new IllegalArgumentException("会话已失效");
                List<SimpleGrantedAuthority> authorities = jdbc.queryForList(
                        "SELECT r.role_code FROM auth_user_role ur JOIN auth_role r ON r.id=ur.role_id "
                                + "WHERE ur.user_id=? AND ur.deleted=0 AND r.deleted=0 AND r.enabled=1 AND (ur.expires_at IS NULL OR ur.expires_at>UTC_TIMESTAMP(3))", String.class, userId)
                        .stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(java.util.Locale.ROOT))).toList();
                var auth = new UsernamePasswordAuthenticationToken(new Principal(userId), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (io.jsonwebtoken.JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"msg\":\"登录已失效，请重新登录\",\"data\":null}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
    public record Principal(Long userId) implements UserIdPrincipal {}
}
