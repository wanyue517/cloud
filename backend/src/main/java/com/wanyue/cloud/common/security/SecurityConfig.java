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
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "X-CSRF-TOKEN", "X-XSRF-TOKEN"));
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
            @Qualifier("corsConfigurationSource") CorsConfigurationSource corsSource)
            throws Exception {

        String[] registerPaths = {
                "/api/admin/auth/login/email",
                "/api/admin/auth/email",
                "/api/admin/auth/register/send-email-code",
                "/api/admin/auth/login/email"
        };

        http
                .cors(cors -> cors.configurationSource(corsSource))
                .authorizeHttpRequests(auth -> auth
                        // 容器内部错误转发应保留原始状态，不能被二次认证覆盖成403。
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, registerPaths).permitAll()
                        .anyRequest().authenticated()
                )
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(registerPaths)
                );

        return http.build();
    }
}
