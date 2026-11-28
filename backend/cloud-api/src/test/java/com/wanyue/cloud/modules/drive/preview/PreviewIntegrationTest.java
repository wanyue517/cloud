package com.wanyue.cloud.modules.drive.preview;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.wanyue.cloud.modules.auth.support.JwtTokenProvider;
import com.wanyue.cloud.modules.drive.DriveModels;
import com.wanyue.cloud.modules.drive.DriveService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PreviewIntegrationTest {
    @MockitoBean com.wanyue.cloud.common.config.RedisStartupCheck redisStartupCheck;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired JwtTokenProvider jwt;
    @Autowired DriveService drive;

    long user;
    long space;
    String token;

    @BeforeEach
    void setup() {
        user = IdWorker.getId();
        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?,'Preview test',1)", user);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)", IdWorker.getId(), user);
        space = Long.parseLong(drive.createSpace(user, new DriveModels.SpaceInput("预览空间", "", "#225588", 0L)));
        token = jwt.createAccessToken(user, 0);
    }

    @Test
    void excelSheetsAndSegmentedRowsWorkAndEnforceOwnership() throws Exception {
        byte[] xlsxBytes;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var s1 = wb.createSheet("销售汇总");
            for (int r = 0; r < 15; r++) {
                var row = s1.createRow(r);
                row.createCell(0).setCellValue("项目-" + r);
                row.createCell(1).setCellValue(r * 100);
            }
            var s2 = wb.createSheet("明细表");
            s2.createRow(0).createCell(0).setCellValue("备注");
            wb.write(out);
            xlsxBytes = out.toByteArray();
        }

        var file = new MockMultipartFile("file", "report.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxBytes);
        String uploaded = mvc.perform(multipart("/api/v1/spaces/" + space + "/files").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String fileId = new tools.jackson.databind.json.JsonMapper().readTree(uploaded).get("data").get("id").asString();

        mvc.perform(get("/api/v1/spaces/" + space + "/nodes/" + fileId + "/preview/excel/sheets")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sheets.length()").value(2))
                .andExpect(jsonPath("$.data.sheets[0].name").value("销售汇总"))
                .andExpect(jsonPath("$.data.sheets[0].rowCount").value(15))
                .andExpect(jsonPath("$.data.sheets[1].name").value("明细表"));

        mvc.perform(get("/api/v1/spaces/" + space + "/nodes/" + fileId + "/preview/excel/rows")
                        .param("sheetIndex", "0").param("rowOffset", "0").param("rowLimit", "10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows.length()").value(10))
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.rows[0][0]").value("项目-0"));

        mvc.perform(get("/api/v1/spaces/" + space + "/nodes/" + fileId + "/preview/excel/rows")
                        .param("sheetIndex", "0").param("rowOffset", "10").param("rowLimit", "10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows.length()").value(5))
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.rows[4][0]").value("项目-14"));

        long stranger = IdWorker.getId();
        db.update("INSERT INTO sys_user(id,display_name,status) VALUES (?,'Stranger',1)", stranger);
        db.update("INSERT INTO auth_user_security(id,user_id) VALUES (?,?)", IdWorker.getId(), stranger);
        String strangerToken = jwt.createAccessToken(stranger, 0);
        mvc.perform(get("/api/v1/spaces/" + space + "/nodes/" + fileId + "/preview/excel/sheets")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void archiveListingAndSafeExtractionBlockZipSlip() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("docs/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("docs/readme.txt"));
            zos.write("hello archive".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("../../evil.txt"));
            zos.write("bad".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        var file = new MockMultipartFile("file", "bundle.zip", "application/zip", baos.toByteArray());
        String uploaded = mvc.perform(multipart("/api/v1/spaces/" + space + "/files").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String fileId = new tools.jackson.databind.json.JsonMapper().readTree(uploaded).get("data").get("id").asString();

        mvc.perform(get("/api/v1/spaces/" + space + "/nodes/" + fileId + "/preview/archive/entries")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.format").value("ZIP"))
                .andExpect(jsonPath("$.data.totalEntries").value(2));

        mvc.perform(get("/api/v1/spaces/" + space + "/nodes/" + fileId + "/preview/archive/extract")
                        .param("entryPath", "docs/readme.txt")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("hello archive"));

        mvc.perform(get("/api/v1/spaces/" + space + "/nodes/" + fileId + "/preview/archive/extract")
                        .param("entryPath", "../../evil.txt")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void videoStrategyDecisionCoversDirectPlayRemuxAndTranscode() {
        var direct = PreviewAssetService.decideVideoStrategy("mov,mp4,m4a,3gp,3g2,mj2", "h264", "aac", 120.5, 1920, 1080, 2_000_000);
        assertEquals("DIRECT_PLAY", direct.strategy());

        var remux = PreviewAssetService.decideVideoStrategy("matroska", "h264", "aac", 60.0, 1280, 720, 1_500_000);
        assertEquals("REMUX", remux.strategy());

        var transcode = PreviewAssetService.decideVideoStrategy("avi", "hevc", "ac3", 45.0, 1920, 1080, 3_000_000);
        assertEquals("TRANSCODE", transcode.strategy());
    }

    @Test
    void videoProbeEndpointReturnsStrategy() throws Exception {
        var file = new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[] {0, 0, 0, 24});
        String uploaded = mvc.perform(multipart("/api/v1/spaces/" + space + "/files").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String fileId = new tools.jackson.databind.json.JsonMapper().readTree(uploaded).get("data").get("id").asString();

        mvc.perform(get("/api/v1/spaces/" + space + "/nodes/" + fileId + "/preview/video/probe")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").exists())
                .andExpect(jsonPath("$.data.strategy").value("UNKNOWN"));
    }
}
