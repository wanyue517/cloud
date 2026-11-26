package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.drive.preview.PreviewModels.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ExcelPreviewService {
    private static final int MAX_ROW_LIMIT = 500;
    private static final int MAX_COLS = 128;

    public ExcelMetadata readMetadata(Path path, String fileName) throws IOException {
        if (isCsv(fileName)) {
            int rows = 0;
            int cols = 0;
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null && rows < 100_000) {
                    rows++;
                    if (rows <= 20) {
                        cols = Math.max(cols, splitCsvLine(line).size());
                    }
                }
            }
            return new ExcelMetadata(List.of(new ExcelSheetInfo(0, "Sheet1", rows, Math.min(cols, MAX_COLS))));
        }
        try (InputStream in = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(in)) {
            List<ExcelSheetInfo> sheets = new ArrayList<>();
            int count = workbook.getNumberOfSheets();
            for (int i = 0; i < count; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                int lastRow = sheet.getPhysicalNumberOfRows() == 0 ? 0 : sheet.getLastRowNum() + 1;
                int maxCols = 0;
                int probeLimit = Math.min(lastRow, 50);
                for (int r = 0; r < probeLimit; r++) {
                    Row row = sheet.getRow(r);
                    if (row != null && row.getLastCellNum() > 0) {
                        maxCols = Math.max(maxCols, Math.min(row.getLastCellNum(), MAX_COLS));
                    }
                }
                sheets.add(new ExcelSheetInfo(i, sheet.getSheetName(), lastRow, maxCols));
            }
            return new ExcelMetadata(sheets);
        } catch (Exception e) {
            throw new BizException(HttpStatus.UNPROCESSABLE_ENTITY, 422, "无法解析 Excel 工作簿: " + e.getMessage());
        }
    }

    public ExcelRowsPage readRows(Path path, String fileName, int sheetIndex, int rowOffset, int rowLimit) throws IOException {
        int safeOffset = Math.max(0, rowOffset);
        int safeLimit = Math.max(1, Math.min(MAX_ROW_LIMIT, rowLimit));
        if (isCsv(fileName)) {
            List<List<String>> pageRows = new ArrayList<>();
            int total = 0;
            int maxCols = 0;
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (total >= safeOffset && pageRows.size() < safeLimit) {
                        List<String> cells = splitCsvLine(line);
                        if (cells.size() > MAX_COLS) cells = cells.subList(0, MAX_COLS);
                        maxCols = Math.max(maxCols, cells.size());
                        pageRows.add(cells);
                    }
                    total++;
                }
            }
            return new ExcelRowsPage(0, "Sheet1", safeOffset, safeLimit, total, maxCols, safeOffset + pageRows.size() < total, pageRows);
        }

        try (InputStream in = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(in)) {
            if (sheetIndex < 0 || sheetIndex >= workbook.getNumberOfSheets()) {
                throw new BizException(HttpStatus.BAD_REQUEST, 400, "工作表索引越界");
            }
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            int totalRows = sheet.getPhysicalNumberOfRows() == 0 ? 0 : sheet.getLastRowNum() + 1;
            int endRow = Math.min(totalRows, safeOffset + safeLimit);
            List<List<String>> rows = new ArrayList<>(Math.max(0, endRow - safeOffset));
            int maxCols = 0;
            for (int r = safeOffset; r < endRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null || row.getLastCellNum() <= 0) {
                    rows.add(List.of());
                    continue;
                }
                int cellCount = Math.min(row.getLastCellNum(), MAX_COLS);
                maxCols = Math.max(maxCols, cellCount);
                List<String> cells = new ArrayList<>(cellCount);
                for (int c = 0; c < cellCount; c++) {
                    Cell cell = row.getCell(c);
                    cells.add(cell == null ? "" : formatter.formatCellValue(cell));
                }
                rows.add(cells);
            }
            return new ExcelRowsPage(sheetIndex, sheet.getSheetName(), safeOffset, safeLimit, totalRows, maxCols, endRow < totalRows, rows);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(HttpStatus.UNPROCESSABLE_ENTITY, 422, "无法读取 Excel 单元格数据: " + e.getMessage());
        }
    }

    private static boolean isCsv(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    private static List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString());
        return cells;
    }
}
