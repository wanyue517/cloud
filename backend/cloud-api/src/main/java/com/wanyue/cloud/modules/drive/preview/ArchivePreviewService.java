package com.wanyue.cloud.modules.drive.preview;

import com.wanyue.cloud.common.exception.BizException;
import com.wanyue.cloud.modules.drive.preview.PreviewModels.*;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
public class ArchivePreviewService {
    private final int maxEntries;
    private final long maxExtractBytes;
    private final double maxCompressionRatio;

    public ArchivePreviewService(
            @Value("${cloud.preview.archive-max-entries:2000}") int maxEntries,
            @Value("${cloud.preview.archive-max-extract-bytes:52428800}") long maxExtractBytes,
            @Value("${cloud.preview.archive-max-compression-ratio:100.0}") double maxCompressionRatio) {
        this.maxEntries = maxEntries;
        this.maxExtractBytes = maxExtractBytes;
        this.maxCompressionRatio = Math.max(10.0, maxCompressionRatio);
    }

    public ArchiveListing listEntries(Path archivePath, String fileName) throws IOException {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            return listZip(archivePath);
        }
        if (lower.endsWith(".tar") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) {
            return listTar(archivePath, lower.endsWith(".gz") || lower.endsWith(".tgz"));
        }
        throw new BizException(HttpStatus.BAD_REQUEST, 400, "暂仅支持 ZIP / TAR / TAR.GZ 归档目录解析");
    }

    public ExtractedEntry extractEntry(Path archivePath, String fileName, String rawEntryPath) throws IOException {
        String normalizedTarget = normalizeSafeEntryPath(rawEntryPath);
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            return extractZipEntry(archivePath, normalizedTarget);
        }
        if (lower.endsWith(".tar") || lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) {
            return extractTarEntry(archivePath, lower.endsWith(".gz") || lower.endsWith(".tgz"), normalizedTarget);
        }
        throw new BizException(HttpStatus.BAD_REQUEST, 400, "暂不支持该压缩格式按需提取");
    }

    public record ExtractedEntry(String fileName, byte[] data) {}

    private ArchiveListing listZip(Path archivePath) throws IOException {
        List<ArchiveEntry> entries = new ArrayList<>();
        boolean truncated = false;
        int total = 0;
        try (ZipFile zip = new ZipFile(archivePath.toFile())) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                String safe = tryNormalizeEntryPath(e.getName());
                if (safe == null || safe.isEmpty()) continue;
                total++;
                if (entries.size() >= maxEntries) {
                    truncated = true;
                    continue;
                }
                entries.add(new ArchiveEntry(
                        safe,
                        extractBaseName(safe),
                        e.isDirectory(),
                        Math.max(0L, e.getSize()),
                        Math.max(0L, e.getCompressedSize()),
                        e.getTime() > 0 ? Instant.ofEpochMilli(e.getTime()).toString() : ""));
            }
        }
        return new ArchiveListing("ZIP", total, truncated, entries);
    }

    private ArchiveListing listTar(Path archivePath, boolean gzip) throws IOException {
        List<ArchiveEntry> entries = new ArrayList<>();
        boolean truncated = false;
        int total = 0;
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(archivePath));
             InputStream unwrapped = gzip ? new GzipCompressorInputStream(raw) : raw;
             TarArchiveInputStream tar = new TarArchiveInputStream(unwrapped)) {
            TarArchiveEntry e;
            while ((e = tar.getNextEntry()) != null) {
                String safe = tryNormalizeEntryPath(e.getName());
                if (safe == null || safe.isEmpty()) continue;
                total++;
                if (entries.size() >= maxEntries) {
                    truncated = true;
                    continue;
                }
                entries.add(new ArchiveEntry(
                        safe,
                        extractBaseName(safe),
                        e.isDirectory(),
                        Math.max(0L, e.getSize()),
                        Math.max(0L, e.getSize()),
                        e.getLastModifiedDate() != null ? e.getLastModifiedDate().toInstant().toString() : ""));
            }
        }
        return new ArchiveListing(gzip ? "TAR.GZ" : "TAR", total, truncated, entries);
    }

    private ExtractedEntry extractZipEntry(Path archivePath, String targetPath) throws IOException {
        try (ZipFile zip = new ZipFile(archivePath.toFile())) {
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                String safe = tryNormalizeEntryPath(e.getName());
                if (targetPath.equals(safe)) {
                    if (e.getSize() > maxExtractBytes) {
                        throw new BizException(HttpStatus.PAYLOAD_TOO_LARGE, 413, "归档内单文件超出在线提取大小上限");
                    }
                    if (e.getCompressedSize() > 0 && e.getSize() > 1024 * 1024
                            && ((double) e.getSize() / (double) e.getCompressedSize()) > maxCompressionRatio) {
                        throw new BizException(HttpStatus.UNPROCESSABLE_ENTITY, 422, "检测到异常高压缩比文件（疑似压缩炸弹），已拒绝提取");
                    }
                    try (InputStream in = zip.getInputStream(e)) {
                        return new ExtractedEntry(extractBaseName(safe), readBounded(in, maxExtractBytes));
                    }
                }
            }
        }
        throw BizException.notFound("压缩包内不存在该文件");
    }

    private ExtractedEntry extractTarEntry(Path archivePath, boolean gzip, String targetPath) throws IOException {
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(archivePath));
             InputStream unwrapped = gzip ? new GzipCompressorInputStream(raw) : raw;
             TarArchiveInputStream tar = new TarArchiveInputStream(unwrapped)) {
            TarArchiveEntry e;
            while ((e = tar.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String safe = tryNormalizeEntryPath(e.getName());
                if (targetPath.equals(safe)) {
                    if (e.getSize() > maxExtractBytes) {
                        throw new BizException(HttpStatus.PAYLOAD_TOO_LARGE, 413, "归档内单文件超出在线提取大小上限");
                    }
                    return new ExtractedEntry(extractBaseName(safe), readBounded(tar, maxExtractBytes));
                }
            }
        }
        throw BizException.notFound("压缩包内不存在该文件");
    }

    private static byte[] readBounded(InputStream in, long maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > maxBytes) {
                throw new BizException(HttpStatus.PAYLOAD_TOO_LARGE, 413, "解压输出超出安全大小上限");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    public static String normalizeSafeEntryPath(String rawPath) {
        String safe = tryNormalizeEntryPath(rawPath);
        if (safe == null || safe.isEmpty()) {
            throw new BizException(HttpStatus.BAD_REQUEST, 400, "非法归档条目路径");
        }
        return safe;
    }

    static String tryNormalizeEntryPath(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String unix = raw.replace('\\', '/').trim();
        if (unix.startsWith("/") || unix.contains("\0")) return null;
        while (unix.startsWith("./")) unix = unix.substring(2);
        while (unix.endsWith("/")) unix = unix.substring(0, unix.length() - 1);
        if (unix.isEmpty()) return null;
        String[] parts = unix.split("/");
        List<String> clean = new ArrayList<>(parts.length);
        for (String p : parts) {
            if (p.isEmpty() || ".".equals(p)) continue;
            if ("..".equals(p) || p.contains(":")) return null;
            clean.add(p);
        }
        return clean.isEmpty() ? null : String.join("/", clean);
    }

    private static String extractBaseName(String normalizedPath) {
        int idx = normalizedPath.lastIndexOf('/');
        return idx >= 0 ? normalizedPath.substring(idx + 1) : normalizedPath;
    }
}
