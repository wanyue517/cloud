package com.wanyue.cloud.modules.drive;

import com.wanyue.cloud.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.*;
import java.io.*;
import java.security.*;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class LocalStorage {
    private final Path root;
    public LocalStorage(@Value("${cloud.storage.root:data/objects}") String root) throws IOException {
        this.root = Path.of(root).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }
    public record Stored(String key, long size, String sha256) {}
    public Stored write(InputStream input, long maxBytes) throws IOException {
        String key = UUID.randomUUID().toString();
        Path target = resolve(key);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long count = 0;
            try (OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    count += read;
                    if (count > maxBytes) throw new IOException("文件大小超过限制");
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                }
            }
            return new Stored(key, count, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException e) {
            Files.deleteIfExists(target);
            throw new IOException("无法保存文件", e);
        }
    }
    public Path resolve(String key) {
        if (key == null || !key.matches("[a-zA-Z0-9-]+")) throw BizException.notFound("对象不存在");
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw BizException.notFound("对象不存在");
        return path;
    }
    public void delete(String key) throws IOException { Files.deleteIfExists(resolve(key)); }
}
