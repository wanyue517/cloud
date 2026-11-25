package com.wanyue.cloud.common.security;

/** 可选：自定义 Spring Security Principal 实现此接口，供审计填充读取用户 ID。 */
public interface UserIdPrincipal {
    Long userId();
}
