package com.wanyue.cloud.common.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.wanyue.cloud.common.security.UserIdPrincipal;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** BaseDO 公共审计字段自动填充，时间统一存储 UTC。 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        Long userId = currentUserId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        setFieldValByName("createdBy", userId, metaObject);
        setFieldValByName("createdAt", now, metaObject);
        setFieldValByName("updatedBy", userId, metaObject);
        setFieldValByName("updatedAt", now, metaObject);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        setFieldValByName("updatedBy", currentUserId(), metaObject);
        setFieldValByName("updatedAt", LocalDateTime.now(ZoneOffset.UTC), metaObject);
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserIdPrincipal user) {
            return user.userId();
        }
        return 0L;
    }
}
