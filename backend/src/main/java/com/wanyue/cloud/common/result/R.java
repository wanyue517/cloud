package com.wanyue.cloud.common.result;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一 HTTP API 业务响应。
 * 注意：code 为业务响应码；HTTP 状态码由 Controller 或异常处理器单独设置。
 *
 * @param <T> 响应数据类型
 */
@Getter
public final class R<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int SUCCESS_CODE = 0;
    public static final int FAIL_CODE = 500;

    private final int code;
    private final String msg;
    private final T data;

    private R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /** 成功，不返回数据。 */
    public static <T> R<T> ok() {
        return new R<>(SUCCESS_CODE, "操作成功", null);
    }

    /** 成功，携带数据。 */
    public static <T> R<T> ok(T data) {
        return new R<>(SUCCESS_CODE, "操作成功", data);
    }

    /** 失败，默认错误信息。 */
    public static <T> R<T> fail() {
        return new R<>(FAIL_CODE, "操作失败", null);
    }

    /** 失败，自定义错误信息。 */
    public static <T> R<T> fail(String msg) {
        return new R<>(FAIL_CODE, msg, null);
    }

    /** 失败，自定义业务错误码及错误信息。 */
    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, msg, null);
    }
}
