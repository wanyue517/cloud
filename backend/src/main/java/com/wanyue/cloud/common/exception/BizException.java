package com.wanyue.cloud.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 业务异常；同时指定业务错误码及 HTTP 状态。 */
@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final HttpStatus httpStatus;

    public BizException(HttpStatus httpStatus, int code, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static BizException notFound(String message) {
        return new BizException(HttpStatus.NOT_FOUND, 404, message);
    }

    public static BizException conflict(String message) {
        return new BizException(HttpStatus.CONFLICT, 409, message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(HttpStatus.UNAUTHORIZED, 401, message);
    }
}
