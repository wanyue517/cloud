package com.wanyue.cloud.modules.space.enums;

public enum SpaceMemberRoleEnum {

    OWNER("OWNER"),
    MANAGER("MANAGER"),
    EDITOR("EDITOR"),
    VIEWER("VIEWER");

    private final String value;

    SpaceMemberRoleEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

}
