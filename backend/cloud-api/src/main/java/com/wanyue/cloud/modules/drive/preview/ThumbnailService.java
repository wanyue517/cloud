package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.modules.drive.DriveModels;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import com.wanyue.cloud.common.exception.BizException;
import org.springframework.http.HttpStatus;
import java.awt.*;
import java.awt.geom.Dimension2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 全类型文件缩略图生成与缓存服务：
 * 支持图片、视频抽帧、音频专辑封面、PDF 首页、PPTX 首页、DOCX 正文、XLSX/XLS/CSV 表格、代码/Markdown/文本、ZIP 目录预览。
 * 产物按 versionId 缓存于 previewRoot/thumbs，源文件替换时自动隔离新旧版本缓存。
 */
@Service
public class ThumbnailService {
    private static final Set<String> IMAGE_EXTS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");
    private static final Set<String> VIDEO_EXTS = Set.of("mp4", "webm", "mov", "mkv", "avi", "m4v", "flv", "wmv", "ogv");
    private static final Set<String> AUDIO_EXTS = Set.of("mp3", "flac", "m4a", "ogg", "opus", "aac", "wav");
    private static final Set<String> SHEET_EXTS = Set.of("xlsx", "xls", "csv");
    private static final Set<String> CODE_EXTS = Set.of(
            "js", "mjs", "cjs", "jsx", "ts", "mts", "cts", "tsx", "vue",
            "html", "htm", "css", "scss", "less", "json", "xml", "yaml", "yml",
            "java", "kt", "py", "go", "rs", "c", "cpp", "h", "hpp", "cs",
            "php", "rb", "sql", "sh", "bash", "ps1", "toml", "ini", "conf", "properties"
    );
    private static final Set<String> TEXT_EXTS = Set.of("md", "markdown", "mdx", "txt", "log", "env");

    private static final long MAX_IMAGE_PIXELS = 40_000_000L;
    private static final long MAX_PDF_BYTES = 100L * 1024 * 1024;
    private static final long MAX_OFFICE_BYTES = 50L * 1024 * 1024;

    private final Path thumbDir;
    private final String ffmpegBin;
    // Bound both working decoders and request threads waiting for generation.
    private final Semaphore generationSlots = new Semaphore(2, true);
    private final Semaphore admissionSlots = new Semaphore(18, true);
    private final ConcurrentHashMap<Path, CompletableFuture<Optional<Path>>> inFlight = new ConcurrentHashMap<>();

    public ThumbnailService(
            @Value("${cloud.storage.preview-root:data/previews}") String previewRoot,
            @Value("${cloud.preview.ffmpeg-bin:ffmpeg}") String ffmpegBin) throws IOException {
        this.thumbDir = Path.of(previewRoot, "thumbs").toAbsolutePath().normalize();
        Files.createDirectories(this.thumbDir);
        this.ffmpegBin = ffmpegBin;
    }

    public Optional<Path> getOrGenerate(long versionId, DriveModels.Content source, int requestedWidth) {
        int width = requestedWidth <= 240 ? 240 : (requestedWidth <= 480 ? 480 : 640);
        Path cached = thumbDir.resolve(versionId + "_" + width + ".jpg").normalize();
        if (Files.isRegularFile(cached)) {
            return Optional.of(cached);
        }
        if (!admissionSlots.tryAcquire()) throw busy();
        var task = new CompletableFuture<Optional<Path>>();
        var existing = inFlight.putIfAbsent(cached, task);
        boolean acquired = false;
        try {
            if (existing != null) return existing.get(10, TimeUnit.SECONDS);
            acquired = generationSlots.tryAcquire(5, TimeUnit.SECONDS);
            if (!acquired) throw busy();
            // Another generation may have completed between the first cache check and admission.
            Optional<Path> result = Files.isRegularFile(cached)
                    ? Optional.of(cached) : generate(source, cached, width);
            task.complete(result);
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            task.completeExceptionally(e);
            throw busy();
        } catch (TimeoutException e) {
            throw busy();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException cause) throw cause;
            if (e.getCause() instanceof Error cause) throw cause;
            throw busy();
        } catch (RuntimeException | Error e) {
            task.completeExceptionally(e);
            throw e;
        } finally {
            if (acquired) generationSlots.release();
            if (existing == null) inFlight.remove(cached, task);
            admissionSlots.release();
        }
    }

    private static BizException busy() {
        return new BizException(HttpStatus.SERVICE_UNAVAILABLE, 503, "缩略图生成繁忙，请稍后重试");
    }

    private Optional<Path> generate(DriveModels.Content source, Path cached, int width) {
        String ext = extensionOf(source.name());
        if (IMAGE_EXTS.contains(ext)) {
            return generateImageThumb(source.path(), cached, width);
        }
        if (VIDEO_EXTS.contains(ext)) {
            return generateVideoPoster(source.path(), cached, width);
        }
        if (AUDIO_EXTS.contains(ext)) {
            return generateAudioCover(source.path(), cached, width);
        }
        if ("pdf".equals(ext)) {
            return generatePdfThumb(source.path(), cached, width);
        }
        if ("pptx".equals(ext)) {
            return generatePptxThumb(source.path(), cached, width);
        }
        if ("docx".equals(ext)) {
            return generateDocxThumb(source.path(), cached, width);
        }
        if (SHEET_EXTS.contains(ext)) {
            return generateSheetThumb(source.path(), ext, cached, width);
        }
        if ("zip".equals(ext)) {
            return generateZipThumb(source.path(), cached, width);
        }
        if (CODE_EXTS.contains(ext)) {
            return generateTextCardThumb(source.path(), ext.toUpperCase(Locale.ROOT), cached, width, true);
        }
        if (TEXT_EXTS.contains(ext)) {
            return generateTextCardThumb(source.path(), ext.toUpperCase(Locale.ROOT), cached, width, false);
        }
        return Optional.empty();
    }

    Optional<Path> generateImageThumb(Path src, Path target, int maxWidth) {
        try (ImageInputStream input = ImageIO.createImageInputStream(src.toFile())) {
            if (input == null) return Optional.empty();
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                // ImageIO 不原生支持 webp 等格式时，回退到 FFmpeg 转换
                return runFfmpegFrame(src, target, List.of(
                        ffmpegBin, "-loglevel", "error", "-y", "-i", src.toAbsolutePath().toString(),
                        "-vframes", "1", "-vf", "scale='min(" + maxWidth + ",iw)':-2"));
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                BufferedImage img = readSampledImage(reader, maxWidth);
                if (img == null) return Optional.empty();
                try {
                    return writeScaledJpeg(img, target, maxWidth);
                } finally {
                    img.flush();
                }
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static BufferedImage readSampledImage(ImageReader reader, int maxWidth) throws IOException {
        int w = reader.getWidth(0);
        int h = reader.getHeight(0);
        if (w <= 0 || h <= 0 || (long) w * h > MAX_IMAGE_PIXELS) return null;
        // Preserve the requested width for ordinary portraits; bound tall images too.
        int sample = Math.max(1, Math.max(w / maxWidth, h / (maxWidth * 2)));
        var param = reader.getDefaultReadParam();
        param.setSourceSubsampling(sample, sample, 0, 0);
        return reader.read(0, param);
    }

    private Optional<Path> generateVideoPoster(Path src, Path target, int maxWidth) {
        // 先用快速前置 -ss 00:00:01 跳过黑帧，不足 1 秒的短视频再回退到 0 秒抽帧
        Optional<Path> at1s = runFfmpegFrame(src, target, List.of(
                ffmpegBin, "-loglevel", "error", "-y", "-ss", "00:00:01", "-i", src.toAbsolutePath().toString(),
                "-vframes", "1", "-vf", "scale='min(" + maxWidth + ",iw)':-2"));
        if (at1s.isPresent()) return at1s;
        return runFfmpegFrame(src, target, List.of(
                ffmpegBin, "-loglevel", "error", "-y", "-i", src.toAbsolutePath().toString(),
                "-vframes", "1", "-vf", "scale='min(" + maxWidth + ",iw)':-2"));
    }

    private Optional<Path> generateAudioCover(Path src, Path target, int maxWidth) {
        return runFfmpegFrame(src, target, List.of(
                ffmpegBin, "-loglevel", "error", "-y", "-i", src.toAbsolutePath().toString(),
                "-an", "-vcodec", "mjpeg", "-vframes", "1", "-vf", "scale='min(" + maxWidth + ",iw)':-2"));
    }

    private Optional<Path> runFfmpegFrame(Path src, Path target, List<String> baseCmd) {
        try {
            Path tmp = Files.createTempFile(thumbDir, "ff-", ".tmp.jpg");
            try {
                List<String> cmd = new ArrayList<>(baseCmd);
                cmd.add(tmp.toAbsolutePath().toString());
                // 必须丢弃/消费子进程输出，否则 Windows/Linux 管道缓冲区写满会导致 FFmpeg 死锁超时
                Process proc = new ProcessBuilder(cmd)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
                boolean ok = proc.waitFor(12, TimeUnit.SECONDS);
                if (!ok) {
                    proc.destroyForcibly();
                    return Optional.empty();
                }
                if (proc.exitValue() == 0 && Files.isRegularFile(tmp) && Files.size(tmp) > 0) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                    return Optional.of(target);
                }
                return Optional.empty();
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<Path> generatePdfThumb(Path src, Path target, int maxWidth) {
        try {
            if (Files.size(src) > MAX_PDF_BYTES) return Optional.empty();
            try (PDDocument doc = Loader.loadPDF(src.toFile())) {
                if (doc.getNumberOfPages() <= 0) return Optional.empty();
                PDFRenderer renderer = new PDFRenderer(doc);
                BufferedImage page = renderer.renderImageWithDPI(0, 96f, ImageType.RGB);
                return writeScaledJpeg(page, target, maxWidth);
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<Path> generatePptxThumb(Path src, Path target, int maxWidth) {
        try {
            if (Files.size(src) > MAX_OFFICE_BYTES) return Optional.empty();
            try (InputStream in = Files.newInputStream(src); XMLSlideShow ppt = new XMLSlideShow(in)) {
                List<XSLFSlide> slides = ppt.getSlides();
                if (slides.isEmpty()) return Optional.empty();
                Dimension2D pageSize = ppt.getPageSize();
                double pw = Math.max(1.0, pageSize.getWidth());
                double ph = Math.max(1.0, pageSize.getHeight());
                double scale = maxWidth / pw;
                int w = Math.max(1, maxWidth);
                int h = Math.max(1, (int) Math.round(ph * scale));
                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                try {
                    enableQualityHints(g);
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, w, h);
                    g.scale(scale, scale);
                    slides.get(0).draw(g);
                } finally {
                    g.dispose();
                }
                return writeJpeg(img, target);
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<Path> generateDocxThumb(Path src, Path target, int maxWidth) {
        try {
            if (Files.size(src) > MAX_OFFICE_BYTES) return Optional.empty();
            List<String> lines = new ArrayList<>();
            try (InputStream in = Files.newInputStream(src); XWPFDocument doc = new XWPFDocument(in)) {
                for (XWPFParagraph p : doc.getParagraphs()) {
                    String text = p.getText();
                    if (text == null) continue;
                    String cleaned = text.replaceAll("\\s+", " ").trim();
                    if (cleaned.isEmpty()) continue;
                    lines.add(cleaned);
                    if (lines.size() >= 18) break;
                }
            }
            if (lines.isEmpty()) lines.add("(空文档)");
            return renderDocumentCard("DOCX", new Color(37, 99, 235), lines, target, maxWidth, false);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<Path> generateSheetThumb(Path src, String ext, Path target, int maxWidth) {
        try {
            if (Files.size(src) > MAX_OFFICE_BYTES) return Optional.empty();
            List<List<String>> rows = new ArrayList<>();
            int maxCols = 5;
            int maxRows = 10;
            if ("csv".equals(ext)) {
                try (BufferedReader br = Files.newBufferedReader(src, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = br.readLine()) != null && rows.size() < maxRows) {
                        String[] parts = line.split(",", -1);
                        List<String> cells = new ArrayList<>();
                        for (int c = 0; c < Math.min(maxCols, parts.length); c++) {
                            cells.add(parts[c].replace("\"", "").trim());
                        }
                        rows.add(cells);
                    }
                }
            } else {
                try (InputStream in = Files.newInputStream(src); Workbook wb = WorkbookFactory.create(in)) {
                    if (wb.getNumberOfSheets() <= 0) return Optional.empty();
                    Sheet sheet = wb.getSheetAt(0);
                    DataFormatter fmt = new DataFormatter();
                    int lastRow = Math.min(sheet.getLastRowNum(), maxRows - 1);
                    for (int r = 0; r <= lastRow; r++) {
                        Row row = sheet.getRow(r);
                        List<String> cells = new ArrayList<>();
                        for (int c = 0; c < maxCols; c++) {
                            Cell cell = row == null ? null : row.getCell(c);
                            cells.add(cell == null ? "" : fmt.formatCellValue(cell).trim());
                        }
                        rows.add(cells);
                    }
                }
            }
            if (rows.isEmpty()) return Optional.empty();

            int w = Math.max(240, maxWidth);
            int h = (int) Math.round(w * 0.75);
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            try {
                enableQualityHints(g);
                g.setColor(new Color(250, 252, 250));
                g.fillRect(0, 0, w, h);

                int topBarH = 26;
                g.setColor(new Color(22, 163, 74));
                g.fillRect(0, 0, w, topBarH);
                g.setColor(Color.WHITE);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                g.drawString(ext.toUpperCase(Locale.ROOT) + " 表格预览", 10, 17);

                int pad = 8;
                int tableW = w - pad * 2;
                int tableH = h - topBarH - pad * 2;
                int colCount = Math.max(2, Math.min(maxCols, rows.stream().mapToInt(List::size).max().orElse(4)));
                int rowCount = Math.min(maxRows, Math.max(6, rows.size()));
                int colW = tableW / colCount;
                int rowH = Math.max(16, tableH / rowCount);

                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
                for (int r = 0; r < rowCount; r++) {
                    int y = topBarH + pad + r * rowH;
                    if (y + rowH > h - pad) break;
                    if (r == 0) {
                        g.setColor(new Color(220, 252, 231));
                        g.fillRect(pad, y, colW * colCount, rowH);
                    } else if (r % 2 == 0) {
                        g.setColor(new Color(248, 250, 252));
                        g.fillRect(pad, y, colW * colCount, rowH);
                    }
                    List<String> rowData = r < rows.size() ? rows.get(r) : List.of();
                    for (int c = 0; c < colCount; c++) {
                        int x = pad + c * colW;
                        g.setColor(new Color(226, 232, 240));
                        g.drawRect(x, y, colW, rowH);
                        String val = c < rowData.size() ? rowData.get(c) : "";
                        if (!val.isEmpty()) {
                            g.setColor(r == 0 ? new Color(20, 83, 45) : new Color(51, 65, 85));
                            g.drawString(truncateToWidth(g, val, colW - 8), x + 4, y + rowH - 5);
                        }
                    }
                }
            } finally {
                g.dispose();
            }
            return writeJpeg(img, target);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<Path> generateZipThumb(Path src, Path target, int maxWidth) {
        try {
            List<String> entries = new ArrayList<>();
            int total = 0;
            try (ZipFile zip = new ZipFile(src.toFile())) {
                Enumeration<? extends ZipEntry> en = zip.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    total++;
                    if (entries.size() < 12) {
                        entries.add((e.isDirectory() ? "[目录] " : "· ") + e.getName());
                    }
                }
            }
            if (entries.isEmpty()) entries.add("(空压缩包)");
            return renderDocumentCard("ZIP · " + total + " 项", new Color(217, 119, 6), entries, target, maxWidth, false);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<Path> generateTextCardThumb(Path src, String badge, Path target, int maxWidth, boolean darkCode) {
        try {
            byte[] buf = new byte[4096];
            int read;
            try (InputStream in = Files.newInputStream(src)) {
                read = in.read(buf);
            }
            if (read <= 0) return Optional.empty();
            String text = new String(buf, 0, read, StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>();
            for (String raw : text.split("\\r?\\n")) {
                String clean = raw.replace("\t", "  ");
                lines.add(clean);
                if (lines.size() >= 15) break;
            }
            Color accent = darkCode ? new Color(99, 102, 241) : new Color(71, 85, 105);
            return renderDocumentCard(badge, accent, lines, target, maxWidth, darkCode);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<Path> renderDocumentCard(String badge, Color accent, List<String> lines, Path target, int maxWidth, boolean dark) throws IOException {
        int w = Math.max(240, maxWidth);
        int h = (int) Math.round(w * 0.75);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            enableQualityHints(g);
            g.setColor(dark ? new Color(24, 24, 27) : new Color(252, 252, 253));
            g.fillRect(0, 0, w, h);

            int headerH = 26;
            g.setColor(dark ? new Color(39, 39, 42) : new Color(241, 245, 249));
            g.fillRect(0, 0, w, headerH);
            g.setColor(accent);
            g.fillRect(0, 0, w, 3);

            if (dark) {
                g.setColor(new Color(239, 68, 68));
                g.fillOval(10, 10, 7, 7);
                g.setColor(new Color(245, 158, 11));
                g.fillOval(22, 10, 7, 7);
                g.setColor(new Color(34, 197, 94));
                g.fillOval(34, 10, 7, 7);
            }
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
            FontMetrics fmBadge = g.getFontMetrics();
            int bw = fmBadge.stringWidth(badge) + 12;
            int bx = w - bw - 8;
            g.setColor(accent);
            g.fillRoundRect(bx, 6, bw, 15, 6, 6);
            g.setColor(Color.WHITE);
            g.drawString(badge, bx + 6, 17);

            g.setFont(new Font(dark ? Font.MONOSPACED : Font.SANS_SERIF, Font.PLAIN, 11));
            g.setColor(dark ? new Color(228, 228, 231) : new Color(30, 41, 59));
            int y = headerH + 18;
            int lineH = 15;
            int maxTextW = w - 20;
            for (String line : lines) {
                if (y > h - 8) break;
                g.drawString(truncateToWidth(g, line, maxTextW), 10, y);
                y += lineH;
            }
        } finally {
            g.dispose();
        }
        return writeJpeg(img, target);
    }

    private static void enableQualityHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static String truncateToWidth(Graphics2D g, String text, int maxPx) {
        if (text == null || text.isEmpty()) return "";
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(text) <= maxPx) return text;
        int end = text.length();
        while (end > 1 && fm.stringWidth(text.substring(0, end) + "…") > maxPx) {
            end--;
        }
        return text.substring(0, end) + "…";
    }

    private Optional<Path> writeScaledJpeg(BufferedImage img, Path target, int maxWidth) throws IOException {
        int targetW = Math.min(maxWidth, img.getWidth());
        int targetH = Math.max(1, (int) Math.round(img.getHeight() * ((double) targetW / img.getWidth())));
        BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            enableQualityHints(g);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, targetW, targetH);
            g.drawImage(img, 0, 0, targetW, targetH, null);
        } finally {
            g.dispose();
        }
        return writeJpeg(scaled, target);
    }

    private Optional<Path> writeJpeg(BufferedImage image, Path target) throws IOException {
        Path tmp = Files.createTempFile(thumbDir, "th-", ".tmp.jpg");
        try {
            ImageIO.write(image, "jpg", tmp.toFile());
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return Optional.of(target);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static String extensionOf(String name) {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
