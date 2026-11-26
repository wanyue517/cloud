package com.wanyue.cloud.modules.auth.enums;

public enum UserStatusEnum {

    DISABLED(0, "禁用"),
    NORMAL(1, "正常"),
    FREEZE(2, "冻结");

    private final Integer code;
    private final String desc;
    UserStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    public Integer getCode() {
        return code;
    }
    public String getDesc() {
        return desc;
    }

}
