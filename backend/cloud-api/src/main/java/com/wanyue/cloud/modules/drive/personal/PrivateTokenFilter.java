package com.wanyue.cloud.modules.drive.personal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class PrivateTokenFilter extends OncePerRequestFilter {
    private static final ThreadLocal<String> TOKEN_HOLDER = new ThreadLocal<>();

    public static String currentToken() {
        return TOKEN_HOLDER.get();
    }

    /** Restore the submitting request's encrypted credential for a background task. */
    public static <T> T withToken(String token, java.util.concurrent.Callable<T> task) throws Exception {
        String previous = TOKEN_HOLDER.get();
        try {
            if (token == null || token.isBlank()) TOKEN_HOLDER.remove();
            else TOKEN_HOLDER.set(token);
            return task.call();
        } finally {
            if (previous == null) TOKEN_HOLDER.remove();
            else TOKEN_HOLDER.set(previous);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = request.getHeader("X-Private-Token");
        if (token == null || token.isBlank()) {
            token = request.getParameter("privateToken");
        }
        if (token != null && !token.isBlank()) {
            TOKEN_HOLDER.set(token.strip());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            TOKEN_HOLDER.remove();
        }
    }
}
