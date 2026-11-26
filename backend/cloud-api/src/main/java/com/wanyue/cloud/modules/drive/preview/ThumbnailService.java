package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.modules.drive.DriveModels;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 图片缩放缩略图与视频抽帧封面生成服务（需求 10.6 / 11.7）。
 * 产物按 versionId 缓存于 previewRoot/thumbs，源文件替换时自动隔离新旧版本缓存。
 */
@Service
public class ThumbnailService {
    private static final Set<String> IMAGE_EXTS = Set.of("png", "jpg", "jpeg", "gif", "bmp", "webp");
    private static final Set<String> VIDEO_EXTS = Set.of("mp4", "webm", "mov", "mkv", "avi", "m4v");
    private static final long MAX_PIXELS = 40_000_000L; // 防止超大恶意图片 OOM

    private final Path thumbDir;
    private final String ffmpegBin;

    public ThumbnailService(
            @Value("${cloud.storage.preview-root:data/previews}") String previewRoot,
            @Value("${cloud.preview.ffmpeg-bin:ffmpeg}") String ffmpegBin) throws IOException {
        this.thumbDir = Path.of(previewRoot, "thumbs").toAbsolutePath().normalize();
        Files.createDirectories(this.thumbDir);
        this.ffmpegBin = ffmpegBin;
    }

    public Optional<Path> getOrGenerate(long versionId, DriveModels.Content source, int requestedWidth) {
        int width = requestedWidth <= 240 ? 240 : (requestedWidth <= 480 ? 480 : 640);
        String ext = extensionOf(source.name());
        Path cached = thumbDir.resolve(versionId + "_" + width + ".jpg").normalize();
        if (Files.isRegularFile(cached)) {
            return Optional.of(cached);
        }
        if (IMAGE_EXTS.contains(ext)) {
            return generateImageThumb(source.path(), cached, width);
        }
        if (VIDEO_EXTS.contains(ext)) {
            return generateVideoPoster(source.path(), cached, width);
        }
        return Optional.empty();
    }

    private Optional<Path> generateImageThumb(Path src, Path target, int maxWidth) {
        try {
            BufferedImage img = ImageIO.read(src.toFile());
            if (img == null) return Optional.empty();
            long pixels = (long) img.getWidth() * (long) img.getHeight();
            if (pixels <= 0 || pixels > MAX_PIXELS) return Optional.empty();
            int targetW = Math.min(maxWidth, img.getWidth());
            int targetH = Math.max(1, (int) Math.round(img.getHeight() * ((double) targetW / img.getWidth())));
            BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, targetW, targetH);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.drawImage(img, 0, 0, targetW, targetH, null);
            } finally {
                g.dispose();
            }
            Path tmp = Files.createTempFile(thumbDir, "img-", ".tmp.jpg");
            try {
                ImageIO.write(scaled, "jpg", tmp.toFile());
                Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return Optional.of(target);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<Path> generateVideoPoster(Path src, Path target, int maxWidth) {
        try {
            Path tmp = Files.createTempFile(thumbDir, "vid-", ".tmp.jpg");
            try {
                Process proc = new ProcessBuilder(
                        ffmpegBin, "-y", "-ss", "00:00:01", "-i", src.toAbsolutePath().toString(),
                        "-vframes", "1", "-vf", "scale=" + maxWidth + ":-2", tmp.toAbsolutePath().toString())
                        .redirectErrorStream(true)
                        .start();
                boolean ok = proc.waitFor(10, TimeUnit.SECONDS);
                if (!ok) {
                    proc.destroyForcibly();
                    return Optional.empty();
                }
                if (proc.exitValue() == 0 && Files.isRegularFile(tmp) && Files.size(tmp) > 0) {
                    Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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

    private static String extensionOf(String name) {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
