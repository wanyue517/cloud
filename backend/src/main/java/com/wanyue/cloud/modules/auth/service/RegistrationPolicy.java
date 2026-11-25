package com.wanyue.cloud.modules.auth.service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
@Service
public class RegistrationPolicy {
    private final JdbcTemplate db;
    public RegistrationPolicy(JdbcTemplate db) { this.db=db; }
    public String claimRole() {
        boolean claimed=Boolean.TRUE.equals(db.queryForObject("SELECT admin_claimed FROM system_bootstrap WHERE id=1 FOR UPDATE",Boolean.class));
        if(!claimed) { db.update("UPDATE system_bootstrap SET admin_claimed=TRUE WHERE id=1"); return "SUPER_ADMIN"; }
        return "USER";
    }
}
