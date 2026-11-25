package com.wanyue.cloud.modules.auth.enums;

public enum IdentityTypeEnum {
    USERNAME("username", "用户名"),
    EMAIL("email", "邮箱"),
    PHONE("phone", "手机号");

    private final String code;
    private final String desc;

    IdentityTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
