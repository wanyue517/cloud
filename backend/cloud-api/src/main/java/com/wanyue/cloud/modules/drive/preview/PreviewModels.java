package com.wanyue.cloud.modules.drive.preview;

import java.util.List;

public final class PreviewModels {
    private PreviewModels() {}

    public record PreviewAsset(
            String id,
            String fileId,
            String versionId,
            String assetType,
            String status,
            String mimeType,
            long sizeBytes,
            String errorMessage,
            int attemptCount,
            String updatedAt) {}

    public record ExcelSheetInfo(int index, String name, int rowCount, int colCount) {}

    public record ExcelMetadata(List<ExcelSheetInfo> sheets) {}

    public record ExcelRowsPage(
            int sheetIndex,
            String sheetName,
            int rowOffset,
            int rowLimit,
            int totalRows,
            int colCount,
            boolean hasMore,
            List<List<String>> rows) {}

    public record VideoProbe(
            String container,
            String videoCodec,
            String audioCodec,
            double durationSeconds,
            int width,
            int height,
            long bitrate,
            String strategy,
            String reason) {}

    public record ArchiveEntry(
            String path,
            String name,
            boolean directory,
            long size,
            long compressedSize,
            String modifiedAt) {}

    public record ArchiveListing(
            String format,
            int totalEntries,
            boolean truncated,
            List<ArchiveEntry> entries) {}
}
