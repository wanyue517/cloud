package com.wanyue.cloud.common.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "cloud.cors")
public class CorsProperties {
    @NotEmpty
    private List<@NotBlank String> allowedOrigins = List.of(
            "http://localhost:3000", "http://127.0.0.1:3000");
}
