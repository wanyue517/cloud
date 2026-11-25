package com.wanyue.cloud.modules.auth;

import com.wanyue.cloud.modules.auth.dataobject.IdentityDO;
import com.wanyue.cloud.modules.auth.dataobject.PasswordCredentialDO;
import com.wanyue.cloud.modules.auth.dataobject.SecurityLogDO;
import com.wanyue.cloud.modules.auth.enums.IdentityTypeEnum;
import com.wanyue.cloud.modules.auth.service.IdentityService;
import com.wanyue.cloud.modules.auth.service.PasswordCredentialService;
import com.wanyue.cloud.modules.auth.service.SecurityLogService;
import com.wanyue.cloud.modules.sys.dataobject.UserDO;
import com.wanyue.cloud.modules.sys.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** 对真实 MySQL 验证默认时间、关联查询和唯一约束；测试业务数据均回滚。 */
@SpringBootTest
@Transactional
class AuthDatabaseModelTest {
    @Autowired UserService users;
    @Autowired IdentityService identities;
    @Autowired PasswordCredentialService credentials;
    @Autowired SecurityLogService logs;
    @Autowired JdbcTemplate jdbc;

    private UserDO newUser() {
        UserDO user = new UserDO();
        user.setDisplayName("database-model-test");
        assertTrue(users.save(user));
        return user;
    }

    @Test
    void databaseDefaultsAndJavaAuditFieldsUseUtc() {
        UserDO user = newUser();
        PasswordCredentialDO credential = new PasswordCredentialDO();
        credential.setUserId(user.getId());
        credential.setPasswordHash("test-hash");
        assertTrue(credentials.save(credential));
        SecurityLogDO log = new SecurityLogDO();
        log.setUserId(user.getId());
        log.setEventType("MODEL_TEST");
        assertTrue(logs.save(log));
        assertEquals(0, jdbc.queryForObject(
                "SELECT TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(), NOW())", Integer.class));
        assertTrue(Math.abs(jdbc.queryForObject(
                "SELECT TIMESTAMPDIFF(SECOND, created_at, registered_at) FROM sys_user WHERE id=?",
                Long.class, user.getId())) <= 1);
        assertTrue(Math.abs(jdbc.queryForObject(
                "SELECT TIMESTAMPDIFF(SECOND, created_at, password_changed_at) FROM auth_password_credential WHERE id=?",
                Long.class, credential.getId())) <= 1);
        assertTrue(Math.abs(jdbc.queryForObject(
                "SELECT TIMESTAMPDIFF(SECOND, created_at, occurred_at) FROM auth_security_log WHERE id=?",
                Long.class, log.getId())) <= 1);
        assertNotEquals(user.getId(), credential.getId());
        assertEquals(credential.getId(), credentials.getByUserId(user.getId()).getId());
        assertNull(credentials.getByUserId(-1L));
    }

    @Test
    void bindingSwitchingAndDeletingPreserveOnePrimaryPerType() {
        UserDO user = newUser();
        String firstEmail = UUID.randomUUID() + "@example.com";
        String secondEmail = UUID.randomUUID() + "@example.com";
        identities.bindEmail(user.getId(), firstEmail);
        identities.bindEmail(user.getId(), secondEmail);
        IdentityDO first = identities.getByEmail(firstEmail);
        IdentityDO second = identities.getByEmail(secondEmail);
        assertTrue(first.getIsPrimary());
        assertFalse(second.getIsPrimary());
        identities.setPrimary(user.getId(), second.getId());
        assertFalse(identities.getById(first.getId()).getIsPrimary());
        assertTrue(identities.getById(second.getId()).getIsPrimary());
        assertThrows(com.wanyue.cloud.common.exception.BizException.class,
                () -> identities.setPrimary(-1L, second.getId()));
        identities.delete(second.getId());
        assertTrue(identities.getById(first.getId()).getIsPrimary());
        assertNull(identities.getById(second.getId()));
    }

    @Test
    void databaseRejectsDuplicatePrimaryEvenWhenBypassingBusinessService() {
        UserDO user = newUser();
        identities.bindEmail(user.getId(), UUID.randomUUID() + "@example.com");
        IdentityDO duplicate = new IdentityDO();
        duplicate.setUserId(user.getId());
        duplicate.setIdentityType(IdentityTypeEnum.EMAIL.getCode());
        duplicate.setIdentifier(UUID.randomUUID() + "@example.com");
        duplicate.setIsPrimary(true);
        assertThrows(DataIntegrityViolationException.class, () -> identities.save(duplicate));
    }
}
