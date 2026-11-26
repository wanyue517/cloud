package com.wanyue.cloud.modules.space.enums;

public enum SpaceKindEnum {

    PERSONAL("PERSONAL", "个人空间"),
    SHARED("SHARED", "共享空间"),
    PRIVATE("PRIVATE", "私密空间");

    private final String code;
    private final String desc;

    SpaceKindEnum(String code, String desc) {
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
