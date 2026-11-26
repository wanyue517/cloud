package com.wanyue.cloud.modules.ops;

import com.wanyue.cloud.modules.drive.storage.DatabaseStorageProvider;
import com.wanyue.cloud.modules.drive.storage.LocalStorageProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 存储对象孤儿扫描与延迟回收服务（T167）。
 * 仅回收无任何 file_version 引用的孤儿 blob_object，绝不误删有效引用内容。
 */
@Service
public class StorageGcService {
    private final JdbcTemplate db;
    private final LocalStorageProvider localStorage;
    private final DatabaseStorageProvider dbStorage;

    public StorageGcService(JdbcTemplate db, LocalStorageProvider localStorage, DatabaseStorageProvider dbStorage) {
        this.db = db;
        this.localStorage = localStorage;
        this.dbStorage = dbStorage;
    }

    public Map<String, Object> scanAndCollect(boolean dryRun, int graceMinutes) {
        int safeGrace = Math.max(0, graceMinutes);
        var orphans = db.queryForList(
                "SELECT b.id, b.storage_key, b.size_bytes, COALESCE(p.provider, 'LOCAL') provider "
                        + "FROM blob_object b "
                        + "LEFT JOIN storage_profile p ON p.id = b.storage_profile_id "
                        + "LEFT JOIN file_version v ON v.blob_id = b.id "
                        + "WHERE v.id IS NULL AND b.created_at <= TIMESTAMPADD(MINUTE, -?, UTC_TIMESTAMP(3)) "
                        + "LIMIT 500",
                safeGrace);

        int deletedCount = 0;
        long reclaimedBytes = 0L;
        List<String> orphanIds = new ArrayList<>();

        for (var row : orphans) {
            long blobId = ((Number) row.get("id")).longValue();
            String key = String.valueOf(row.get("storage_key"));
            long size = ((Number) row.get("size_bytes")).longValue();
            String provider = String.valueOf(row.get("provider"));
            orphanIds.add(String.valueOf(blobId));

            if (!dryRun) {
                // 二次引用保护校验，防止并发上传恰好引用了该 blob
                Integer refs = db.queryForObject("SELECT COUNT(*) FROM file_version WHERE blob_id=?", Integer.class, blobId);
                if (refs != null && refs > 0) continue;
                try {
                    if ("DB".equalsIgnoreCase(provider)) {
                        dbStorage.delete(key);
                    } else if ("LOCAL".equalsIgnoreCase(provider)) {
                        localStorage.delete(key);
                    }
                } catch (Exception ignored) {
                }
                int removed = db.update("DELETE FROM blob_object WHERE id=?", blobId);
                if (removed == 1) {
                    deletedCount++;
                    reclaimedBytes += size;
                }
            } else {
                reclaimedBytes += size;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dryRun", dryRun);
        result.put("graceMinutes", safeGrace);
        result.put("orphanCount", orphans.size());
        result.put("deletedCount", deletedCount);
        result.put("reclaimedBytes", reclaimedBytes);
        result.put("orphanIds", orphanIds);
        return result;
    }
}
