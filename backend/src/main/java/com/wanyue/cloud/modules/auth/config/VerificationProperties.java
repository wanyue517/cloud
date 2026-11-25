package com.wanyue.cloud.modules.auth.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "cloud.verification")
public class VerificationProperties {
    @NotBlank
    @Size(min = 32)
    private String hmacSecret;
    @Min(1)
    private int codeTtlSeconds = 300;
    @Min(1)
    private int sendIntervalSeconds = 60;
    @Min(1)
    private int maxFailures = 5;
    @Min(1)
    private int ipHourlyLimit = 20;
    @Min(1)
    private int targetDailyLimit = 10;
    @Min(1)
    private int globalHourlyLimit = 1000;
}
