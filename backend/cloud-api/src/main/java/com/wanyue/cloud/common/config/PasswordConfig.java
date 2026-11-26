package com.wanyue.cloud.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(
                16,     // salt 长度
                32,     // hash 长度
                1,      // 并行度
                19456,  // 内存成本，单位 KiB
                2       // 迭代次数
        );
    }
}
