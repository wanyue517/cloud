package com.wanyue.cloud.modules.auth.support;

import com.wanyue.cloud.common.exception.BizException;
import org.springframework.http.HttpStatus;

import java.util.Locale;

public final class EmailNormalizer {
    private EmailNormalizer() {}

    public static String normalize(String email) {
        if (email == null || email.isBlank()) {
            throw new BizException(HttpStatus.BAD_REQUEST, 400, "邮箱不能为空");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
