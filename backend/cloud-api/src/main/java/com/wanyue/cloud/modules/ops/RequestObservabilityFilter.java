package com.wanyue.cloud.modules.ops;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 全链路请求观测与基础指标采集过滤器（T173）。
 * 采集 /api/** 请求耗时、错误率、P95 延迟与最近 100 条链路日志供管理后台实时观测。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestObservabilityFilter extends OncePerRequestFilter {
    private static final int MAX_RECENT = 100;
    private static final int MAX_LATENCY_SAMPLES = 500;

    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong errorRequests = new AtomicLong();
    private final ConcurrentLinkedDeque<Map<String, Object>> recent = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Long> latencies = new ConcurrentLinkedDeque<>();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String uri = req.getRequestURI();
        if (uri == null || !uri.startsWith("/api/")) {
            chain.doFilter(req, res);
            return;
        }
        long startNs = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            long durationMs = Math.max(0L, (System.nanoTime() - startNs) / 1_000_000L);
            int status = res.getStatus();
            totalRequests.incrementAndGet();
            if (status >= 400) {
                errorRequests.incrementAndGet();
            }
            latencies.addLast(durationMs);
            while (latencies.size() > MAX_LATENCY_SAMPLES) {
                latencies.pollFirst();
            }
            String traceId = res.getHeader("X-Trace-Id");
            if (traceId == null || traceId.isBlank()) {
                traceId = req.getHeader("X-Trace-Id");
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("traceId", traceId == null ? "" : traceId);
            entry.put("method", req.getMethod());
            entry.put("path", uri);
            entry.put("status", status);
            entry.put("durationMs", durationMs);
            entry.put("at", Instant.now().toString());
            recent.addFirst(entry);
            while (recent.size() > MAX_RECENT) {
                recent.pollLast();
            }
        }
    }

    public Map<String, Object> snapshot() {
        long total = totalRequests.get();
        long errors = errorRequests.get();
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        long p95 = sorted.isEmpty() ? 0L : sorted.get(Math.min(sorted.size() - 1, (int) Math.floor(sorted.size() * 0.95)));
        long avg = sorted.isEmpty() ? 0L : Math.round(sorted.stream().mapToLong(Long::longValue).average().orElse(0));

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalRequests", total);
        metrics.put("errorRequests", errors);
        metrics.put("errorRate", total == 0 ? 0.0 : Math.round((errors * 10000.0) / total) / 100.0);
        metrics.put("avgLatencyMs", avg);
        metrics.put("p95LatencyMs", p95);

        return Map.of("metrics", metrics, "recentRequests", new ArrayList<>(recent));
    }
}
