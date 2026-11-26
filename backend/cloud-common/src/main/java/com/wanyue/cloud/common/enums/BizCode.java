package com.wanyue.cloud.common.enums;

public enum BizCode {

    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),
    PARAMS_VALID_ERROR(400, "参数校验失败"),
    USER_NOT_LOGIN(401, "用户未登录"),
    USER_NOT_AUTH(403, "用户无权限");

    private int code;
    private String msg;
    BizCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
