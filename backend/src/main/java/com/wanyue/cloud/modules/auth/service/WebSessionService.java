package com.wanyue.cloud.modules.auth.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

@Service
public class WebSessionService {
    private final JdbcTemplate db;
    private final JwtTokenProvider tokens;
    private final byte[] key;
    public WebSessionService(JdbcTemplate db,JwtTokenProvider tokens,@Value("${cloud.auth.jwt.secret}") String secret) {
        this.db=db;this.tokens=tokens;this.key=Base64.getDecoder().decode(secret);
    }
    public record SessionTokens(String accessToken,long expiresIn,String refreshToken) {}
    private String random() { byte[] value=new byte[32];new SecureRandom().nextBytes(value);return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private String hash(String token) {
        try { Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(token.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    @Transactional
    public String start(long user) {
        String refresh=random();
        int version=db.queryForObject("SELECT session_version FROM auth_user_security WHERE user_id=? AND deleted=0",Integer.class,user);
        db.update("INSERT INTO auth_session(id,user_id,refresh_token_hash,refresh_expires_at,session_version) VALUES (?,?,?,DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 30 DAY),?)",IdWorker.getId(),user,hash(refresh),version);
        return refresh;
    }
    @Transactional
    public SessionTokens refresh(String refresh) {
        if(refresh==null || refresh.length()>200) throw BizException.unauthorized("会话已失效");
        var sessions=db.queryForList("SELECT s.id,s.user_id,s.session_version FROM auth_session s JOIN sys_user u ON u.id=s.user_id JOIN auth_user_security sec ON sec.user_id=u.id "
                + "WHERE s.refresh_token_hash=? AND s.deleted=0 AND s.revoked_at IS NULL AND s.refresh_expires_at>UTC_TIMESTAMP(3) AND u.status=1 AND u.deleted=0 AND sec.deleted=0 AND sec.session_version=s.session_version FOR UPDATE",hash(refresh));
        if(sessions.isEmpty()) throw BizException.unauthorized("会话已失效");
        var session=sessions.get(0);String next=random();long user=((Number)session.get("user_id")).longValue();int version=((Number)session.get("session_version")).intValue();
        db.update("UPDATE auth_session SET refresh_token_hash=?,rotation_count=rotation_count+1,last_used_at=UTC_TIMESTAMP(3),last_rotated_at=UTC_TIMESTAMP(3) WHERE id=?",hash(next),session.get("id"));
        return new SessionTokens(tokens.createAccessToken(user,version),tokens.getAccessTokenTtlSeconds(),next);
    }
    @Transactional
    public void logout(long user) {
        db.update("UPDATE auth_user_security SET session_version=session_version+1 WHERE user_id=? AND deleted=0",user);
        db.update("UPDATE auth_session SET revoked_at=UTC_TIMESTAMP(3),revoke_reason='LOGOUT' WHERE user_id=? AND revoked_at IS NULL",user);
    }
}
