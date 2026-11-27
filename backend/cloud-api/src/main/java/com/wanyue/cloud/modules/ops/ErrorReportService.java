package com.wanyue.cloud.modules.ops;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.security.UserIdPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * 后台全局错误上报与持久记录服务：
 * 任何接口抛错均通过独立事务记录至 sys_error_report，供管理端实时查看与闭环处理。
 */
@Service
public class ErrorReportService {
    private static final int MAX_STACK_CHARS = 65536;
    private final JdbcTemplate db;

    public ErrorReportService(JdbcTemplate db) {
        this.db = db;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(HttpServletRequest req, int httpStatus, int errorCode, String message, Throwable ex) {
        try {
            String traceId = MDC.get("traceId");
            if (traceId == null) traceId = "";
            String method = req != null && req.getMethod() != null ? req.getMethod() : "";
            String path = req != null && req.getRequestURI() != null ? req.getRequestURI() : "";
            Long userId = resolveCurrentUserId();
            String level = httpStatus >= 500 ? "ERROR" : "WARN";
            String exClass = ex != null ? ex.getClass().getName() : "";
            String safeMsg = message == null ? (ex != null && ex.getMessage() != null ? ex.getMessage() : "未知异常") : message;
            if (safeMsg.length() > 980) safeMsg = safeMsg.substring(0, 980);
            String stack = formatStack(ex);
            db.update(
                    "INSERT INTO sys_error_report(id, trace_id, level, http_status, error_code, method, path, user_id, exception_class, message, stack_trace, resolved, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, UTC_TIMESTAMP(3))",
                    IdWorker.getId(), traceId, level, httpStatus, errorCode, method, path, userId, exClass, safeMsg, stack);
        } catch (Exception ignored) {
            // 错误上报自身绝不能影响主链路响应
        }
    }

    public Map<String, Object> list(Boolean resolved, String level, int limit) {
        int safeLimit = Math.max(1, Math.min(200, limit));
        Long total = db.queryForObject("SELECT COUNT(*) FROM sys_error_report", Long.class);
        Long unresolved = db.queryForObject("SELECT COUNT(*) FROM sys_error_report WHERE resolved=0", Long.class);
        Long serverErrors = db.queryForObject("SELECT COUNT(*) FROM sys_error_report WHERE resolved=0 AND http_status>=500", Long.class);

        StringBuilder sql = new StringBuilder(
                "SELECT CAST(id AS CHAR) id, trace_id traceId, level, http_status httpStatus, error_code errorCode, "
                        + "method, path, CAST(user_id AS CHAR) userId, exception_class exceptionClass, message, "
                        + "stack_trace stackTrace, resolved, resolved_at resolvedAt, created_at createdAt "
                        + "FROM sys_error_report WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (resolved != null) {
            sql.append(" AND resolved=?");
            args.add(resolved ? 1 : 0);
        }
        if (level != null && !level.isBlank() && !"ALL".equalsIgnoreCase(level)) {
            sql.append(" AND level=?");
            args.add(level.trim().toUpperCase(Locale.ROOT));
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ?");
        args.add(safeLimit);

        var items = db.queryForList(sql.toString(), args.toArray());
        for (var row : items) {
            Object res = row.get("resolved");
            boolean isResolved = (res instanceof Boolean b) ? b : (res instanceof Number n && n.intValue() != 0);
            row.put("resolved", isResolved);
            row.put("createdAt", toUtcIso(row.get("createdAt")));
            row.put("resolvedAt", toUtcIso(row.get("resolvedAt")));
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total == null ? 0L : total);
        summary.put("unresolved", unresolved == null ? 0L : unresolved);
        summary.put("serverErrors", serverErrors == null ? 0L : serverErrors);
        return Map.of("summary", summary, "items", items);
    }

    public void resolve(long id) {
        int updated = db.update("UPDATE sys_error_report SET resolved=1, resolved_at=UTC_TIMESTAMP(3) WHERE id=?", id);
        if (updated != 1) throw BizException.notFound("错误记录不存在");
    }

    public int clearResolved() {
        return db.update("DELETE FROM sys_error_report WHERE resolved=1");
    }

    private static Long resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserIdPrincipal principal) {
            return principal.userId();
        }
        return null;
    }

    private static String toUtcIso(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime ldt) {
            return ldt.toInstant(ZoneOffset.UTC).toString();
        }
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime().toInstant(ZoneOffset.UTC).toString();
        }
        return value.toString();
    }

    private static String formatStack(Throwable ex) {
        if (ex == null) return null;
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        String full = sw.toString();
        return full.length() > MAX_STACK_CHARS ? full.substring(0, MAX_STACK_CHARS) : full;
    }
}
