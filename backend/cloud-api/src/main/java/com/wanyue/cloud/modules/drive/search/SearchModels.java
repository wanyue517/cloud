package com.wanyue.cloud.modules.drive.search;

import java.util.List;

public final class SearchModels {
    private SearchModels() {}

    public record SearchHit(
            String id,
            String spaceId,
            String spaceName,
            String parentId,
            String name,
            String nodeType,
            String category,
            long size,
            String mimeType,
            String updatedAt,
            String matchSource,
            String snippet) {}

    public record SearchResponse(
            List<SearchHit> items,
            long total,
            int page,
            int pageSize,
            boolean hasMore) {}

    public record IndexStatus(
            long indexedChunks,
            long indexedFiles,
            long pendingEvents,
            long failedEvents,
            boolean meilisearchConfigured,
            boolean tikaConfigured) {}
}
