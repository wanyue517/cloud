package com.wanyue.cloud.modules.drive.search;

import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

@Service
public class TextExtractionService {
    private static final Set<String> PLAIN_TEXT_EXTS = Set.of(
            "txt", "md", "markdown", "java", "kt", "js", "ts", "tsx", "jsx", "vue",
            "json", "yaml", "yml", "xml", "html", "htm", "css", "scss", "sql",
            "py", "go", "rs", "c", "h", "cpp", "sh", "ps1", "log", "csv", "ini", "properties");
    private static final Set<String> TIKA_OFFICE_EXTS = Set.of("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "rtf", "odt");

    private final String tikaUrl;
    private final long maxIndexBytes;
    private final HttpClient httpClient;

    public TextExtractionService(
            @Value("${cloud.search.tika-url:}") String tikaUrl,
            @Value("${cloud.search.max-index-bytes:20971520}") long maxIndexBytes) {
        this.tikaUrl = tikaUrl == null ? "" : tikaUrl.strip().replaceAll("/+$", "");
        this.maxIndexBytes = Math.max(1024L * 1024, maxIndexBytes);
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public boolean isTikaConfigured() {
        return !tikaUrl.isEmpty();
    }

    public boolean isIndexable(String fileName, long sizeBytes) {
        if (fileName == null || sizeBytes <= 0 || sizeBytes > maxIndexBytes) return false;
        String ext = extensionOf(fileName);
        return PLAIN_TEXT_EXTS.contains(ext) || TIKA_OFFICE_EXTS.contains(ext);
    }

    /** Safely extracts indexable text; unsupported or corrupt files return empty string without throwing (T136). */
    public String extractText(Path filePath, String fileName, long sizeBytes) {
        if (!isIndexable(fileName, sizeBytes) || !Files.isRegularFile(filePath)) return "";
        String ext = extensionOf(fileName);
        try {
            if (PLAIN_TEXT_EXTS.contains(ext)) {
                byte[] bytes = Files.readAllBytes(filePath);
                for (int i = 0; i < Math.min(bytes.length, 4096); i++) {
                    if (bytes[i] == 0) return "";
                }
                return new String(bytes, StandardCharsets.UTF_8);
            }
            if (!tikaUrl.isEmpty()) {
                String extracted = callTika(filePath);
                if (!extracted.isBlank()) return extracted;
            }
            if ("xlsx".equals(ext) || "xls".equals(ext)) {
                return extractExcelFallback(filePath);
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    private String callTika(Path filePath) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(tikaUrl + "/tika"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "text/plain")
                    .PUT(HttpRequest.BodyPublishers.ofFile(filePath))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resp.statusCode() == 200 && resp.body() != null ? resp.body() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String extractExcelFallback(Path filePath) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = Files.newInputStream(filePath);
             Workbook wb = WorkbookFactory.create(in)) {
            DataFormatter fmt = new DataFormatter(Locale.CHINA);
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                Sheet sheet = wb.getSheetAt(s);
                int maxRows = Math.min(sheet.getLastRowNum() + 1, 2000);
                for (int r = 0; r < maxRows; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    int cols = Math.min(row.getLastCellNum(), 64);
                    for (int c = 0; c < cols; c++) {
                        Cell cell = row.getCell(c);
                        if (cell != null) {
                            String v = fmt.formatCellValue(cell).trim();
                            if (!v.isEmpty()) sb.append(v).append(' ');
                        }
                    }
                    sb.append('\n');
                    if (sb.length() > 500_000) return sb.toString();
                }
            }
        }
        return sb.toString();
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
