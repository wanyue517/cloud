package com.wanyue.cloud.common.exception;

import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.modules.ops.ErrorReportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * 统一业务异常和系统异常响应，并自动上报至后台错误记录表（sys_error_report）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ErrorReportService errorReportService;

    public GlobalExceptionHandler(ErrorReportService errorReportService) {
        this.errorReportService = errorReportService;
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<R<Void>> denied(org.springframework.security.access.AccessDeniedException e, HttpServletRequest req) {
        errorReportService.record(req, 403, 403, "无权限访问", e);
        return ResponseEntity.status(403).body(R.fail(403, "无权限访问"));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<R<Void>> conflict(org.springframework.dao.DataIntegrityViolationException e, HttpServletRequest req) {
        log.warn("数据约束冲突 traceId={}", org.slf4j.MDC.get("traceId"));
        errorReportService.record(req, 409, 409, "数据已存在或状态冲突，请刷新后重试", e);
        return ResponseEntity.status(409).body(R.fail(409, "数据已存在或状态冲突，请刷新后重试"));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<R<Void>> tooLarge(org.springframework.web.multipart.MaxUploadSizeExceededException e, HttpServletRequest req) {
        errorReportService.record(req, 413, 413, "上传请求过大，请使用分片上传", e);
        return ResponseEntity.status(413).body(R.fail(413, "上传请求过大，请使用分片上传"));
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<R<Void>> badParameter(Exception e, HttpServletRequest req) {
        errorReportService.record(req, 400, 400, "请求参数错误", e);
        return ResponseEntity.badRequest().body(R.fail(400, "请求参数错误"));
    }

    @ExceptionHandler({RedisConnectionFailureException.class, RedisSystemException.class, QueryTimeoutException.class})
    public ResponseEntity<R<Void>> handleInfrastructureUnavailable(RuntimeException e, HttpServletRequest req) {
        log.error("基础服务连接或执行失败", e);
        errorReportService.record(req, 503, 503, "服务暂不可用，请稍后重试", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(R.fail(503, "服务暂不可用，请稍后重试"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Void>> handleUnreadableRequest(HttpMessageNotReadableException e, HttpServletRequest req) {
        errorReportService.record(req, 400, 400, "请求JSON格式错误", e);
        return ResponseEntity.badRequest().body(R.fail(400, "请求JSON格式错误"));
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBizException(BizException e, HttpServletRequest req) {
        errorReportService.record(req, e.getHttpStatus().value(), e.getCode(), e.getMessage(), e);
        return ResponseEntity.status(e.getHttpStatus())
                .body(R.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleValidation(MethodArgumentNotValidException e, HttpServletRequest req) {
        var fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null ? "请求参数校验失败" : fieldError.getDefaultMessage();
        errorReportService.record(req, 400, 400, message, e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(400, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<R<Void>> handleConstraintViolation(ConstraintViolationException e, HttpServletRequest req) {
        errorReportService.record(req, 400, 400, "请求参数校验失败", e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(400, "请求参数校验失败"));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<R<Void>> handleMethodValidation(HandlerMethodValidationException e, HttpServletRequest req) {
        errorReportService.record(req, 400, 400, "请求参数校验失败", e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(400, "请求参数校验失败"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleUnexpected(Exception e, HttpServletRequest req) {
        if (isClientAbort(e)) {
            log.warn("客户端主动断开或上传超时中断 traceId={} path={}", org.slf4j.MDC.get("traceId"), req != null ? req.getRequestURI() : "");
            return ResponseEntity.status(499).build();
        }
        log.error("未捕获系统异常 traceId={}", org.slf4j.MDC.get("traceId"), e);
        errorReportService.record(req, 500, 500, e.getMessage() == null ? "服务器内部错误" : e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(500, "服务器内部错误，请稍后重试"));
    }

    private static boolean isClientAbort(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            String name = cur.getClass().getName();
            if (name.endsWith("ClientAbortException") || name.endsWith("AsyncRequestNotUsableException")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
