package com.wanyue.cloud.modules.auth.enums;

public enum SecurityLogEventTypeEnum {
    EMAIL_REGISTER("email_register", "邮箱注册");

    private String code;
    private String desc;

    SecurityLogEventTypeEnum(String code, String desc) {
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
