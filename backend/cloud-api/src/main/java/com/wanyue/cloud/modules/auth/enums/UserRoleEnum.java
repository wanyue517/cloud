package com.wanyue.cloud.modules.auth.enums;

public enum UserRoleEnum {
    SUPER_ADMIN("super_admin", "超级管理员"),
    ADMIN("admin", "管理员"),
    USER("user", "普通用户");

    private final String code;
    private final String desc;

    UserRoleEnum(String code, String desc) {
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
