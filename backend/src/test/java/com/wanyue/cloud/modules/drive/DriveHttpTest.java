package com.wanyue.cloud.modules.drive;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import com.wanyue.cloud.modules.auth.service.WebSessionService;
import com.wanyue.cloud.modules.auth.service.RegistrationPolicy;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DriveHttpTest {
    @MockitoBean com.wanyue.cloud.common.config.RedisStartupCheck redisStartupCheck;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired JwtTokenProvider jwt;
    @Autowired DriveService drive;
    @Autowired WebSessionService sessions;
    @Autowired RegistrationPolicy policy;
    long user,space;
    String token;
    @BeforeEach void setup() {
        user=IdWorker.getId();
        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?,'HTTP test',1)",user);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)",IdWorker.getId(),user);
        space=Long.parseLong(drive.createSpace(user,new DriveModels.SpaceInput("HTTP空间","","#112233",0L)));
        token=jwt.createAccessToken(user,0);
    }
    @Test void bearerIsRequiredAndStringIdsAndRangeWork() throws Exception {
        mvc.perform(get("/api/v1/spaces")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/spaces").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(String.valueOf(space)));
        var file=new MockMultipartFile("file","range.txt","text/plain","abcdef".getBytes());
        String response=mvc.perform(multipart("/api/v1/spaces/"+space+"/files").file(file).header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").isString()).andReturn().getResponse().getContentAsString();
        var tree=new tools.jackson.databind.json.JsonMapper().readTree(response);
        String id=tree.get("data").get("id").asString();
        mvc.perform(get("/api/v1/spaces/"+space+"/nodes/"+id+"/content").header("Authorization","Bearer "+token).header("Range","bytes=1-3"))
                .andExpect(status().isPartialContent()).andExpect(content().string("bcd")).andExpect(header().string("Content-Range","bytes 1-3/6"));
        mvc.perform(get("/api/v1/spaces/"+space+"/nodes/"+id+"/content").header("Authorization","Bearer "+token).header("Range","bytes=100-200"))
                .andExpect(status().isRequestedRangeNotSatisfiable());
    }
    @Test void revokedAndDisabledAccountsAndOrdinaryAdminAccessAreRejected() throws Exception {
        mvc.perform(get("/api/admin/space/space/page").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        db.update("UPDATE sys_user SET status=0 WHERE id=?",user);
        mvc.perform(get("/api/v1/spaces").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        db.update("UPDATE sys_user SET status=1 WHERE id=?",user);
        sessions.logout(user);
        mvc.perform(get("/api/v1/spaces").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
    @Test void refreshRotatesAndOldRefreshCannotBeReused() {
        String refresh=sessions.start(user);
        var next=sessions.refresh(refresh);
        assertNotEquals(refresh,next.refreshToken());assertEquals(String.valueOf(user),jwt.verify(next.accessToken()).getSubject());
        assertThrows(com.wanyue.cloud.common.exception.BizException.class,()->sessions.refresh(refresh));
        sessions.logout(user);
        assertThrows(com.wanyue.cloud.common.exception.BizException.class,()->sessions.refresh(next.refreshToken()));
    }
    @Test void bootstrapClaimIsSingleUseWithinTransaction() {
        db.update("UPDATE system_bootstrap SET admin_claimed=FALSE WHERE id=1");
        assertEquals("SUPER_ADMIN",policy.claimRole());assertEquals("USER",policy.claimRole());
    }
    @Test void adminApiRequiresRoleAndCannotDisableCurrentAdmin() throws Exception {
        mvc.perform(get("/api/v1/admin/stats").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        db.update("INSERT INTO auth_user_role(id,user_id,role_id) VALUES (?,?,1)",IdWorker.getId(),user);
        mvc.perform(get("/api/v1/admin/stats").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.users").isNumber());
        mvc.perform(put("/api/v1/admin/users/"+user+"/status").header("Authorization","Bearer "+token).contentType("application/json").content("{\"status\":0}"))
                .andExpect(status().isConflict());
    }
    @Test void profileUpdateIsScopedToCurrentAccount() throws Exception {
        mvc.perform(put("/api/v1/me").header("Authorization","Bearer "+token).contentType("application/json").content("{\"displayName\":\"New name\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me").header("Authorization","Bearer "+token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("New name")).andExpect(jsonPath("$.data.id").isString());
        mvc.perform(put("/api/v1/me").header("Authorization","Bearer "+token).contentType("application/json").content("{\"displayName\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }
}
