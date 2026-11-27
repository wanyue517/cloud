package com.wanyue.cloud.common.security;

import com.wanyue.cloud.common.config.CorsProperties;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "Range", "X-CSRF-TOKEN", "X-XSRF-TOKEN", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Accept-Ranges", "Content-Range", "Content-Length"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        // 携带凭据时必须使用明确来源，禁止误配置为通配符。
        configuration.validateAllowCredentials();
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            AccessTokenFilter accessTokenFilter,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource corsSource)
            throws Exception {

        String[] registerPaths = {
                "/api/admin/auth/login/email",
                "/api/admin/auth/email",
                "/api/admin/auth/register/send-email-code",
                "/api/admin/auth/password/send-code",
                "/api/admin/auth/password/reset"
        };

        http
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(session -> session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .addFilterBefore(accessTokenFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"msg\":\"请先登录\",\"data\":null}");
                }))
                .cors(cors -> cors.configurationSource(corsSource))
                .authorizeHttpRequests(auth -> auth
                        // 容器内部错误转发应保留原始状态，不能被二次认证覆盖成403。
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/site-config").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/assets/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/oauth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/shares/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/shares/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/media/stream").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/v1/media/stream").permitAll()
                        .requestMatchers(HttpMethod.POST, registerPaths).permitAll()
                        .requestMatchers(HttpMethod.POST,"/api/v1/auth/refresh").permitAll()
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
