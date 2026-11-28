package com.wanyue.cloud.modules.auth;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.common.exception.GlobalExceptionHandler;
import com.wanyue.cloud.common.security.SecurityConfig;
import com.wanyue.cloud.modules.auth.controller.AuthController;
import com.wanyue.cloud.modules.auth.service.AuthService;
import com.wanyue.cloud.modules.auth.vo.UserLoginRespVO;
import com.wanyue.cloud.modules.sys.vo.UserRespVO;
import com.wanyue.cloud.modules.auth.service.VerificationRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import jakarta.servlet.DispatcherType;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(AuthEmailEndpointTest.Config.class)
@WebAppConfiguration
class AuthEmailEndpointTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    @Import({AuthController.class, SecurityConfig.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean com.wanyue.cloud.modules.auth.service.WebSessionService webSessions() {
            var sessions=mock(com.wanyue.cloud.modules.auth.service.WebSessionService.class);
            when(sessions.start(anyLong(), nullable(String.class), nullable(String.class), nullable(String.class), nullable(String.class)))
                    .thenReturn("test-refresh");return sessions;
        }
        @Bean com.wanyue.cloud.common.security.AccessTokenFilter accessTokenFilter() {
            return new com.wanyue.cloud.common.security.AccessTokenFilter(
                    mock(com.wanyue.cloud.modules.auth.support.JwtTokenProvider.class),
                    mock(org.springframework.jdbc.core.JdbcTemplate.class));
        }
        @Bean AuthService authService() { return mock(AuthService.class); }
        @Bean VerificationRateLimiter limiter() { return mock(VerificationRateLimiter.class); }
        @Bean com.wanyue.cloud.modules.ops.ErrorReportService errorReports() { return mock(com.wanyue.cloud.modules.ops.ErrorReportService.class); }
        @Bean TestErrorController errorController() { return new TestErrorController(); }
    }

    @RestController
    static class TestErrorController {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter;

        @GetMapping(value="/api/v1/messages/events", produces="text/event-stream")
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream() throws Exception {
            emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(60000L);
            emitter.send("connected");
            return emitter;
        }
        @GetMapping("/error")
        org.springframework.http.ResponseEntity<Void> error() {
            return org.springframework.http.ResponseEntity.status(500).build();
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired AuthService auth;
    @Autowired VerificationRateLimiter limiter;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        reset(auth, limiter);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void anonymousUserCanRequestCodeWithMethodSecurityEnabled() throws Exception {
        mvc.perform(post("/api/admin/auth/register/send-email-code").param("email", "user@example.com"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        verify(limiter).checkIp("127.0.0.1");
        verify(auth).sendEmailCode("user@example.com");
    }

    @Test
    void anonymousLoginReturnsExistingResponseTypeAndStringUserId() throws Exception {
        UserRespVO user = new UserRespVO();
        user.setId(2124534695294148609L);
        when(auth.loginWithEmail(any())).thenReturn(UserLoginRespVO.builder()
                .accessToken("signed-jwt-token").expiresIn(900L).tokenType("Bearer").user(user).build());
        mvc.perform(post("/api/admin/auth/login/email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("signed-jwt-token"))
                .andExpect(jsonPath("$.data.expiresIn").value(900))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.id").isString())
                .andExpect(jsonPath("$.data.user.id").value("2124534695294148609"));
    }

    @Test
    void invalidEmailDoesNotDispatch() throws Exception {
        mvc.perform(post("/api/admin/auth/register/send-email-code").param("email", "invalid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(auth);
    }

    @Test
    void malformedRegistrationJsonReturns400InsteadOf403() throws Exception {
        mvc.perform(post("/api/admin/auth/email").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(auth, limiter);
    }

    @Test
    void redisFailuresReturn503InsteadOfMisleading403() throws Exception {
        doThrow(new RedisConnectionFailureException("redis unavailable"))
                .when(limiter).checkIp(anyString());
        mvc.perform(post("/api/admin/auth/register/send-email-code")
                .param("email", "user@example.com").header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(503))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
        verifyNoInteractions(auth);
        reset(limiter);
        doThrow(new QueryTimeoutException("redis command timed out"))
                .when(auth).sendEmailCode(anyString());
        mvc.perform(post("/api/admin/auth/register/send-email-code").param("email", "user@example.com"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(503));
    }

    @Test
    void internalErrorDispatchPreserves500ButDirectErrorRequestRemainsProtected() throws Exception {
        mvc.perform(get("/error").with(request -> {
                    request.setDispatcherType(DispatcherType.ERROR);
                    return request;
                }))
                .andExpect(status().isInternalServerError());
        mvc.perform(get("/error")).andExpect(status().isUnauthorized());
        verifyNoInteractions(auth, limiter);
    }

    @Test
    void committedResponseCanIncludeContainerErrorPageWithoutReauthorizing() throws Exception {
        var request = new org.springframework.mock.web.MockHttpServletRequest(context.getServletContext(), "GET", "/api/v1/messages/events");
        request.setServletPath("/api/v1/messages/events");
        request.setDispatcherType(DispatcherType.INCLUDE);
        request.setAttribute(jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/messages/events");
        request.setAttribute(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 500);
        request.setAttribute(jakarta.servlet.RequestDispatcher.INCLUDE_REQUEST_URI, "/error");
        request.setAttribute(jakarta.servlet.RequestDispatcher.INCLUDE_SERVLET_PATH, "/error");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        response.setContentType("text/event-stream");
        response.getWriter().write("data: connected\n\n");
        response.flushBuffer();
        var included = new java.util.concurrent.atomic.AtomicBoolean();
        context.getBean(org.springframework.security.web.FilterChainProxy.class)
                .doFilter(request, response, (req, resp) -> included.set(true));
        org.junit.jupiter.api.Assertions.assertTrue(included.get());
        org.junit.jupiter.api.Assertions.assertEquals("data: connected\n\n", response.getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals(200, response.getStatus());
    }

    @Test
    void ordinaryIncludesAndSpoofedHeadersDoNotBypassAuthentication() throws Exception {
        mvc.perform(get("/error").with(request -> {
                    request.setDispatcherType(DispatcherType.INCLUDE);
                    request.setAttribute(jakarta.servlet.RequestDispatcher.INCLUDE_REQUEST_URI, "/error");
                    return request;
                })).andExpect(status().isUnauthorized());
        mvc.perform(get("/error").header("jakarta.servlet.error.request_uri", "/original")
                .header("jakarta.servlet.include.request_uri", "/error"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/protected").with(request -> {
                    request.setDispatcherType(DispatcherType.INCLUDE);
                    request.setAttribute(jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI, "/original");
                    request.setAttribute(jakarta.servlet.RequestDispatcher.INCLUDE_REQUEST_URI, "/api/v1/protected");
                    return request;
                })).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedSseCanCompleteEvenIfAsyncDispatchHasNoAuthentication() throws Exception {
        mvc.perform(get("/api/v1/messages/events")).andExpect(status().isUnauthorized());
        var initial = mvc.perform(get("/api/v1/messages/events")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("test")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted()).andReturn();
        org.junit.jupiter.api.Assertions.assertTrue(initial.getResponse().isCommitted());
        context.getBean(TestErrorController.class).emitter.complete();
        var dispatch = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(initial);
        mvc.perform(servletContext -> org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous()
                        .postProcessRequest(dispatch.buildRequest(servletContext)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("connected")));
    }

    @Test
    void asyncDispatchWithoutAnExistingStreamResultCannotBypassAuthentication() throws Exception {
        mvc.perform(get("/api/v1/messages/events").with(request -> {
            request.setDispatcherType(DispatcherType.ASYNC);
            return request;
        })).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/protected").with(request -> {
            request.setDispatcherType(DispatcherType.ASYNC);
            var manager = mock(org.springframework.web.context.request.async.WebAsyncManager.class);
            when(manager.hasConcurrentResult()).thenReturn(true);
            request.setAttribute(org.springframework.web.context.request.async.WebAsyncUtils.WEB_ASYNC_MANAGER_ATTRIBUTE, manager);
            return request;
        })).andExpect(status().isUnauthorized());
    }

    @Test
    void rateLimitReturnsStructured429BeforeDispatch() throws Exception {
        doThrow(new BizException(HttpStatus.TOO_MANY_REQUESTS, 429, "请求频繁"))
                .when(limiter).checkIp(anyString());
        mvc.perform(post("/api/admin/auth/register/send-email-code").param("email", "user@example.com"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value(429));
        verifyNoInteractions(auth);
    }

    @Test
    void registrationAcceptsFrontendJsonAndReturnsBoolean() throws Exception {
        when(auth.registerWithEmail(any())).thenReturn(true);
        mvc.perform(post("/api/admin/auth/email").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"user@example.com","code":"012345","password":"password123"}
                    """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));
        verify(auth).registerWithEmail(argThat(request -> "012345".equals(request.getCode())
                && "user@example.com".equals(request.getEmail())));
    }

    @Test
    void registrationRejectsShortPassword() throws Exception {
        mvc.perform(post("/api/admin/auth/email").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"user@example.com","code":"012345","password":"123456"}
                    """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(auth);
    }

    @Test
    void corsPreflightIsHandledBeforeAuthentication() throws Exception {
        for (String origin : new String[]{"http://localhost:3000", "http://127.0.0.1:3000"}) {
            mvc.perform(options("/api/admin/auth/email")
                    .header(HttpHeaders.ORIGIN, origin)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,authorization"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        }
        mvc.perform(options("/api/admin/users")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk());
        verifyNoInteractions(auth, limiter);
    }

    @Test
    void corsRejectsUnknownOriginsBeforeDispatch() throws Exception {
        mvc.perform(post("/api/admin/auth/register/send-email-code")
                .param("email", "user@example.com")
                .header(HttpHeaders.ORIGIN, "https://untrusted.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        verifyNoInteractions(auth, limiter);
    }

    @Test
    void corsHeadersArePresentOnSuccessAndBusinessErrors() throws Exception {
        mvc.perform(post("/api/admin/auth/register/send-email-code")
                .param("email", "user@example.com")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
        doThrow(new BizException(HttpStatus.TOO_MANY_REQUESTS, 429, "请求频繁"))
                .when(limiter).checkIp(anyString());
        mvc.perform(post("/api/admin/auth/register/send-email-code")
                .param("email", "user@example.com")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    void corsDoesNotAllowAnonymousAccessToProtectedResources() throws Exception {
        mvc.perform(get("/api/admin/users").header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    void capacitorOriginsCanPreflightAndCallPublicApiWithTheirActualAppHeaders() throws Exception {
        for (String origin : java.util.List.of("capacitor://localhost", "https://localhost")) {
            mvc.perform(options("/api/admin/auth/register/send-email-code")
                    .header(HttpHeaders.ORIGIN, origin)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,x-client-type,x-device-name,x-refresh-token,x-private-token"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
            mvc.perform(post("/api/admin/auth/register/send-email-code")
                    .param("email", "user@example.com").header(HttpHeaders.ORIGIN, origin))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
        }
    }

    @Test
    void nativeAppOriginsStillNeedAuthenticationAndOtherCapacitorHostsAreRejected() throws Exception {
        for (String origin : java.util.List.of("capacitor://localhost", "https://localhost")) {
            mvc.perform(get("/api/admin/users").header(HttpHeaders.ORIGIN, origin))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
        }
        mvc.perform(get("/api/admin/users").header(HttpHeaders.ORIGIN, "capacitor://untrusted.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

}
