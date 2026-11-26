package com.wanyue.cloud.modules.drive.storage;

import com.wanyue.cloud.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 存储适配器注册表：根据 storage_profile.provider 路由至对应 StorageProvider 实例。
 */
@Component
public class StorageRegistry {

    private final Map<String, StorageProvider> providers;

    public StorageRegistry(List<StorageProvider> providers) {
        this.providers = providers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        p -> p.providerType().toUpperCase(Locale.ROOT),
                        Function.identity()));
    }

    public StorageProvider get(String providerType) {
        StorageProvider provider = providerType == null ? null : providers.get(providerType.toUpperCase(Locale.ROOT));
        if (provider == null) {
            throw BizException.notFound("不支持的存储驱动类型: " + providerType);
        }
        return provider;
    }

    public StorageProvider defaultLocal() {
        return get("LOCAL");
    }
}
