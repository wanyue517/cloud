package com.wanyue.cloud.modules.drive.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;

/**
 * 物理存储适配器基础接口（对齐需求文档 6.2 节与 T056）。
 * 后续 S3StorageProvider / DatabaseStorageProvider 均实现此接口并由 StorageRegistry 按 profile 路由。
 */
public interface StorageProvider {

    /** 适配器类型编码：LOCAL / S3 / DATABASE */
    String providerType();

    record StoredObject(String key, long size, String sha256) {}

    record StorageObjectInfo(String key, long size) {}

    /** 流式写入对象并计算 SHA-256，超出 maxBytes 立即中止并清理 */
    StoredObject put(String key, InputStream input, long maxBytes) throws IOException;

    /** 按对象键流式读取完整内容 */
    InputStream get(String key) throws IOException;

    /** 按闭区间 [start, endInclusive] 流式读取部分内容 */
    InputStream getRange(String key, long start, long endInclusive) throws IOException;

    boolean exists(String key);

    void delete(String key) throws IOException;

    StorageObjectInfo stat(String key) throws IOException;

    /** 本地存储可返回物理路径供零拷贝 Range 响应使用；远程存储返回 empty */
    default Optional<Path> resolveLocalPath(String key) {
        return Optional.empty();
    }
}
