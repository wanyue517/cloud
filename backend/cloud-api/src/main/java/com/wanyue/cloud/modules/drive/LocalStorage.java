package com.wanyue.cloud.modules.drive;

import com.wanyue.cloud.modules.drive.storage.LocalStorageProvider;
import com.wanyue.cloud.modules.drive.storage.StorageProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 兼容门面：委托至 StorageProvider / LocalStorageProvider，保持既有调用点零破坏。
 */
@Component
public class LocalStorage {
    private final LocalStorageProvider delegate;

    public LocalStorage(LocalStorageProvider delegate) {
        this.delegate = delegate;
    }

    public record Stored(String key, long size, String sha256) {}

    public Stored write(InputStream input, long maxBytes) throws IOException {
        StorageProvider.StoredObject stored = delegate.put(UUID.randomUUID().toString(), input, maxBytes);
        return new Stored(stored.key(), stored.size(), stored.sha256());
    }

    public Path resolve(String key) {
        return delegate.resolve(key);
    }

    public void delete(String key) throws IOException {
        delegate.delete(key);
    }
}
