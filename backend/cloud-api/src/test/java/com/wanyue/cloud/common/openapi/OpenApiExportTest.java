package com.wanyue.cloud.common.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.wanyue.cloud.common.config.RedisStartupCheck;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "cloud.openapi.enabled=true")
@AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext
class OpenApiExportTest {

    @MockitoBean
    RedisStartupCheck redisStartupCheck;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @SuppressWarnings("unchecked")
    void exportsValidOpenApiSpecAndSyncsContractFile() throws Exception {
        String raw = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Object> spec = objectMapper.readValue(raw, Map.class);
        assertEquals("3.0.3", spec.get("openapi"));
        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        assertNotNull(paths);
        assertTrue(paths.containsKey("/api/v1/spaces"));
        assertTrue(paths.containsKey("/api/v1/spaces/{space}/nodes"));
        assertTrue(paths.containsKey("/api/v1/uploads"));
        assertTrue(paths.containsKey("/api/v1/admin/stats"));
        assertTrue(paths.containsKey("/api/v1/admin/users"));

        Map<String, Object> components = (Map<String, Object>) spec.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
        assertTrue(schemas.containsKey("DriveModels_Space"));
        assertTrue(schemas.containsKey("DriveModels_Node"));
        assertTrue(schemas.containsKey("DriveModels_Page"));
        assertTrue(schemas.containsKey("DriveModels_Version"));
        assertTrue(schemas.containsKey("DriveModels_Upload"));

        if ("true".equals(System.getProperty("cloud.openapi.export"))) {
            Path repoRoot = Path.of("").toAbsolutePath().normalize();
            while (repoRoot != null && !Files.exists(repoRoot.resolve("compose.yaml"))) {
                repoRoot = repoRoot.getParent();
            }
            Path target = (repoRoot != null ? repoRoot : Path.of("../..").toAbsolutePath().normalize())
                    .resolve("docs/api/openapi.json");
            Files.createDirectories(target.getParent());
            String formatted = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(spec) + "\n";
            Files.writeString(target, formatted);
        }
    }
}
