package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.modules.drive.DriveModels;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ThumbnailServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void generatesValidJpegThumbnailsForPdfOfficeCodeCsvAndZip() throws Exception {
        ThumbnailService svc = new ThumbnailService(tempDir.resolve("previews").toString(), "ffmpeg");

        // 1. PDF
        Path pdf = tempDir.resolve("sample.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(pdf.toFile());
        }
        assertValidThumb(svc.getOrGenerate(101L, content(pdf, "sample.pdf", "application/pdf"), 320));

        // 2. DOCX
        Path docx = tempDir.resolve("doc.docx");
        try (XWPFDocument doc = new XWPFDocument(); var out = Files.newOutputStream(docx)) {
            doc.createParagraph().createRun().setText("Cloudlane 测试文档标题");
            doc.createParagraph().createRun().setText("这是一段用于缩略图渲染的正文内容。");
            doc.write(out);
        }
        assertValidThumb(svc.getOrGenerate(102L, content(docx, "doc.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"), 320));

        // 3. PPTX
        Path pptx = tempDir.resolve("deck.pptx");
        try (XMLSlideShow ppt = new XMLSlideShow(); var out = Files.newOutputStream(pptx)) {
            ppt.createSlide().createTextBox().setText("Slide 1");
            ppt.write(out);
        }
        assertValidThumb(svc.getOrGenerate(103L, content(pptx, "deck.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"), 320));

        // 4. XLSX
        Path xlsx = tempDir.resolve("sheet.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook(); var out = Files.newOutputStream(xlsx)) {
            var row = wb.createSheet("Data").createRow(0);
            row.createCell(0).setCellValue("姓名");
            row.createCell(1).setCellValue("分数");
            wb.write(out);
        }
        assertValidThumb(svc.getOrGenerate(104L, content(xlsx, "sheet.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), 320));

        // 5. CSV
        Path csv = tempDir.resolve("table.csv");
        Files.writeString(csv, "id,name,score\n1,Alice,98\n2,Bob,87\n", StandardCharsets.UTF_8);
        assertValidThumb(svc.getOrGenerate(105L, content(csv, "table.csv", "text/csv"), 320));

        // 6. Code (.ts) & Markdown (.md)
        Path code = tempDir.resolve("app.ts");
        Files.writeString(code, "export function hello(name: string) {\n  return `Hi ${name}`\n}\n", StandardCharsets.UTF_8);
        assertValidThumb(svc.getOrGenerate(106L, content(code, "app.ts", "text/plain"), 320));

        Path md = tempDir.resolve("readme.md");
        Files.writeString(md, "# Cloudlane\n支持多种文件缩略图。\n", StandardCharsets.UTF_8);
        assertValidThumb(svc.getOrGenerate(107L, content(md, "readme.md", "text/markdown"), 320));

        // 7. ZIP
        Path zip = tempDir.resolve("bundle.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("notes.txt"));
            zos.write("hello".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        assertValidThumb(svc.getOrGenerate(108L, content(zip, "bundle.zip", "application/zip"), 320));
    }

    private static DriveModels.Content content(Path path, String name, String mime) throws Exception {
        return new DriveModels.Content(path, Files.size(path), name, mime, "etag");
    }

    private static void assertValidThumb(java.util.Optional<Path> result) throws Exception {
        assertTrue(result.isPresent(), "Expected thumbnail to be generated");
        Path p = result.get();
        assertTrue(Files.isRegularFile(p) && Files.size(p) > 0);
        BufferedImage img = ImageIO.read(p.toFile());
        assertNotNull(img, "Thumbnail should be readable JPEG");
        assertTrue(img.getWidth() > 0 && img.getHeight() > 0);
    }
}
