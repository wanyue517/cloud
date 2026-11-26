package com.wanyue.cloud.common.security;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;
@Component
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        String id=UUID.randomUUID().toString();MDC.put("traceId",id);response.setHeader("X-Trace-Id",id);
        try { chain.doFilter(request,response); } finally { MDC.remove("traceId"); }
    }
}
