package com.wanyue.cloud.modules.auth;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QrLoginTest {
    @MockitoBean com.wanyue.cloud.common.config.RedisStartupCheck redisStartupCheck;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired JwtTokenProvider jwt;

    long userId;
    String mobileToken;

    @BeforeEach
    void setup() {
        userId = IdWorker.getId();
        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?,'MobileUser',1)", userId);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)", IdWorker.getId(), userId);
        mobileToken = jwt.createAccessToken(userId, 0);
    }

    @Test
    void qrLoginLifecycleEnforcesAuthAndPreventsReplay() throws Exception {
        // 1. PC creates QR ticket
        String createJson = mvc.perform(post("/api/admin/auth/qr/create"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qrId").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(120))
                .andReturn().getResponse().getContentAsString();
        String qrId = new tools.jackson.databind.json.JsonMapper().readTree(createJson).get("data").get("qrId").asString();

        // 2. Initial status is PENDING
        mvc.perform(get("/api/admin/auth/qr/" + qrId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // 3. Unauthenticated confirm rejected with 401
        mvc.perform(post("/api/v1/auth/qr/" + qrId + "/confirm"))
                .andExpect(status().isUnauthorized());

        // 4. Mobile scans -> SCANNED
        mvc.perform(post("/api/v1/auth/qr/" + qrId + "/scan")
                        .header("Authorization", "Bearer " + mobileToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/auth/qr/" + qrId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCANNED"));

        // 5. Mobile confirms -> PC receives CONFIRMED + accessToken + HttpOnly cookie
        mvc.perform(post("/api/v1/auth/qr/" + qrId + "/confirm")
                        .header("Authorization", "Bearer " + mobileToken))
                .andExpect(status().isOk());

        // Replay confirm before consumption -> 409 conflict
        mvc.perform(post("/api/v1/auth/qr/" + qrId + "/confirm")
                        .header("Authorization", "Bearer " + mobileToken))
                .andExpect(status().isConflict());

        String confirmedJson = mvc.perform(get("/api/admin/auth/qr/" + qrId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.displayName").value("MobileUser"))
                .andExpect(header().string("Set-Cookie", containsString("cloudlane.refresh=")))
                .andReturn().getResponse().getContentAsString();
        assertNotNull(new tools.jackson.databind.json.JsonMapper().readTree(confirmedJson).get("data").get("accessToken").asString());

        // 6. Anti-replay: second status poll returns EXPIRED, second confirm returns 404
        mvc.perform(get("/api/admin/auth/qr/" + qrId + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXPIRED"));
        mvc.perform(post("/api/v1/auth/qr/" + qrId + "/confirm")
                        .header("Authorization", "Bearer " + mobileToken))
                .andExpect(status().isNotFound());
    }
}
