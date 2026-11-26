package com.wanyue.cloud.common.exception;

import com.wanyue.cloud.common.result.R;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * 统一业务异常和常见参数异常响应。安全认证异常由 SecurityFilterChain 的
 * AuthenticationEntryPoint / AccessDeniedHandler 独立处理。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<R<Void>> denied(org.springframework.security.access.AccessDeniedException e) {
        return ResponseEntity.status(403).body(R.fail(403,"无权限访问"));
    }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<R<Void>> conflict(org.springframework.dao.DataIntegrityViolationException e) {
        log.warn("数据约束冲突 traceId={}",org.slf4j.MDC.get("traceId"));
        return ResponseEntity.status(409).body(R.fail(409,"数据已存在或状态冲突，请刷新后重试"));
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<R<Void>> tooLarge(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        return ResponseEntity.status(413).body(R.fail(413,"上传请求过大，请使用分片上传"));
    }
    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<R<Void>> badParameter(Exception e) {
        return ResponseEntity.badRequest().body(R.fail(400,"请求参数错误"));
    }

    @ExceptionHandler({RedisConnectionFailureException.class, RedisSystemException.class, QueryTimeoutException.class})
    public ResponseEntity<R<Void>> handleInfrastructureUnavailable(RuntimeException e) {
        log.error("基础服务连接或执行失败", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(R.fail(503, "服务暂不可用，请稍后重试"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Void>> handleUnreadableRequest(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(R.fail(400, "请求JSON格式错误"));
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBizException(BizException e) {
        return ResponseEntity.status(e.getHttpStatus())
                .body(R.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<R<Void>> handleValidation(MethodArgumentNotValidException e) {
        var fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null ? "请求参数校验失败" : fieldError.getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(400, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<R<Void>> handleConstraintViolation(ConstraintViolationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(400, "请求参数校验失败"));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<R<Void>> handleMethodValidation(HandlerMethodValidationException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(R.fail(400, "请求参数校验失败"));
    }
}
