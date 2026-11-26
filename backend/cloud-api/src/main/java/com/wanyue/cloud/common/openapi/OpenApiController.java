package com.wanyue.cloud.common.openapi;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * OpenAPI 3.0.3 规范导出端点，默认仅在开启 cloud.openapi.enabled=true 时注册。
 */
@RestController
@ConditionalOnProperty(name = "cloud.openapi.enabled", havingValue = "true")
public class OpenApiController {

    private final OpenApiSpecBuilder builder;

    public OpenApiController(OpenApiSpecBuilder builder) {
        this.builder = builder;
    }

    @GetMapping("/v3/api-docs")
    public Map<String, Object> apiDocs() {
        return builder.build();
    }
}
