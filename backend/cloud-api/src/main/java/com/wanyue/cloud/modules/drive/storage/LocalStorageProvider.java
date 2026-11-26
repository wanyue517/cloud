package com.wanyue.cloud.modules.drive.storage;

import com.wanyue.cloud.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 本地文件系统存储适配器（T057）：安全路径防穿越、流式 SHA-256 计算、Range 切片读取与失败自动清理。
 */
@Component
public class LocalStorageProvider implements StorageProvider {

    private final Path root;

    public LocalStorageProvider(@Value("${cloud.storage.root:data/objects}") String root) throws IOException {
        this.root = Path.of(root).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    @Override
    public String providerType() {
        return "LOCAL";
    }

    @Override
    public StoredObject put(String key, InputStream input, long maxBytes) throws IOException {
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
            return new StoredObject(key, count, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException e) {
            Files.deleteIfExists(target);
            throw new IOException("无法保存文件", e);
        }
    }

    @Override
    public InputStream get(String key) throws IOException {
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) throw BizException.notFound("对象不存在");
        return Files.newInputStream(path, StandardOpenOption.READ);
    }

    @Override
    public InputStream getRange(String key, long start, long endInclusive) throws IOException {
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) throw BizException.notFound("对象不存在");
        long size = Files.size(path);
        if (start < 0 || endInclusive < start || endInclusive >= size) {
            throw new IllegalArgumentException("Range 范围无效");
        }
        FileChannel channel = FileChannel.open(path, StandardOpenOption.READ);
        channel.position(start);
        long remaining = endInclusive - start + 1;
        InputStream raw = Channels.newInputStream(channel);
        return new InputStream() {
            private long left = remaining;

            @Override
            public int read() throws IOException {
                if (left <= 0) return -1;
                int b = raw.read();
                if (b != -1) left--;
                return b;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (left <= 0) return -1;
                int toRead = (int) Math.min(len, left);
                int n = raw.read(b, off, toRead);
                if (n > 0) left -= n;
                return n;
            }

            @Override
            public void close() throws IOException {
                raw.close();
            }
        };
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    @Override
    public StorageObjectInfo stat(String key) throws IOException {
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) throw BizException.notFound("对象不存在");
        return new StorageObjectInfo(key, Files.size(path));
    }

    @Override
    public Optional<Path> resolveLocalPath(String key) {
        return Optional.of(resolve(key));
    }

    public Path resolve(String key) {
        if (key == null || !key.matches("[a-zA-Z0-9-]+")) throw BizException.notFound("对象不存在");
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) throw BizException.notFound("对象不存在");
        return path;
    }
}
