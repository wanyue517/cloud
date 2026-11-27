package com.wanyue.cloud.modules.auth;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.modules.auth.service.WebSessionService;
import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MobileSessionDualModeTest {
    @MockitoBean com.wanyue.cloud.common.config.RedisStartupCheck redisStartupCheck;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired JwtTokenProvider jwt;
    @Autowired WebSessionService sessions;

    long user;
    String accessToken;

    @BeforeEach
    void setup() {
        user = IdWorker.getId();
        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?,'Mobile Session Test',1)", user);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)", IdWorker.getId(), user);
        accessToken = jwt.createAccessToken(user, 0);
    }

    @Test
    void normalizeClientTypeAndMobileDetectionWork() {
        assertEquals("WEB", WebSessionService.normalizeClientType(null));
        assertEquals("WEB", WebSessionService.normalizeClientType("unknown"));
        assertEquals("MOBILE_H5", WebSessionService.normalizeClientType("mobile_h5"));
        assertEquals("IOS", WebSessionService.normalizeClientType("ios"));
        assertEquals("ANDROID", WebSessionService.normalizeClientType(" ANDROID "));
        assertFalse(WebSessionService.isMobileAppClient("WEB"));
        assertFalse(WebSessionService.isMobileAppClient("MOBILE_H5"));
        assertTrue(WebSessionService.isMobileAppClient("IOS"));
        assertTrue(WebSessionService.isMobileAppClient("ANDROID"));
    }

    @Test
    void mobileH5UsesCookieRefreshAndRecordsDeviceMetadata() throws Exception {
        String refresh = sessions.start(user, "Mozilla/5.0 (iPhone)", "10.0.0.8", "MOBILE_H5", "iPhone 16 Safari");

        mvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + accessToken)
                        .cookie(new Cookie("cloudlane.refresh", refresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].clientType").value("MOBILE_H5"))
                .andExpect(jsonPath("$.data[0].deviceName").value("iPhone 16 Safari"))
                .andExpect(jsonPath("$.data[0].ipAddress").value("10.0.0.8"))
                .andExpect(jsonPath("$.data[0].current").value(true));

        mvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "Cloudlane")
                        .header("X-Client-Type", "MOBILE_H5")
                        .cookie(new Cookie("cloudlane.refresh", refresh)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    @Test
    void iosAndAndroidUseHeaderTokenRefreshWithRotationAndNoCookie() throws Exception {
        String refresh = sessions.start(user, "Cloudlane-iOS/1.0", "10.0.0.9", "IOS", "iPhone 16 Pro");

        // Missing X-Requested-With must still be rejected with 403
        mvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Client-Type", "IOS")
                        .header("X-Refresh-Token", refresh))
                .andExpect(status().isForbidden());

        String respJson = mvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "Cloudlane")
                        .header("X-Client-Type", "IOS")
                        .header("X-Refresh-Token", refresh))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andReturn().getResponse().getContentAsString();

        var tree = new JsonMapper().readTree(respJson);
        String rotatedRefresh = tree.get("data").get("refreshToken").asString();
        assertNotEquals(refresh, rotatedRefresh);

        // Old refresh token cannot be reused after rotation
        mvc.perform(post("/api/v1/auth/refresh")
                        .header("X-Requested-With", "Cloudlane")
                        .header("X-Client-Type", "IOS")
                        .header("X-Refresh-Token", refresh))
                .andExpect(status().isUnauthorized());

        // Session list recognizes current device via X-Refresh-Token header
        mvc.perform(get("/api/v1/auth/sessions")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("X-Refresh-Token", rotatedRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].clientType").value("IOS"))
                .andExpect(jsonPath("$.data[0].deviceName").value("iPhone 16 Pro"))
                .andExpect(jsonPath("$.data[0].current").value(true));
    }
}
