package com.wanyue.cloud.modules.drive;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;

public final class DriveModels {
    private DriveModels() {}
    public record Space(String id, String name, String kind, String icon, String color, String description, long sortOrder, String role, String ownerName) {
        public Space(String id, String name, String kind, String icon, String color, String description, long sortOrder) {
            this(id, name, kind, icon, color, description, sortOrder, "OWNER", "");
        }
    }
    public record SpaceInput(@NotBlank @Size(max=50) String name, @Size(max=500) String description,
                             @Pattern(regexp="#[0-9a-fA-F]{6}") String color, Long sortOrder, @Size(max=64) String icon,
                             @Pattern(regexp="PERSONAL|SHARED|PRIVATE") String kind) {
        public SpaceInput(String name, String description, String color, Long sortOrder) {
            this(name, description, color, sortOrder, "", "PERSONAL");
        }
        public SpaceInput(String name, String description, String color, Long sortOrder, String icon) {
            this(name, description, color, sortOrder, icon, "PERSONAL");
        }
    }
    public record Node(String id, String spaceId, String parentId, String name, String nodeType,
                       String currentVersionId, long revision, long size, String mimeType,
                       boolean hidden, String updatedAt, boolean hasChildren) {}
    public record NodeInput(@NotBlank @Size(max=255) String name, @PositiveOrZero long parentId) {}
    public record ChangeInput(@NotBlank @Size(max=255) String name, @PositiveOrZero long parentId,
                              @PositiveOrZero long revision) {}
    public record TransferInput(@Positive long targetSpaceId, @PositiveOrZero long targetParentId,
                                @Pattern(regexp="MOVE|COPY") String mode, @PositiveOrZero long revision) {}
    public record DirectoryStats(long folders, long files, Map<String, Long> categories) {}
    public record Page(List<Node> items, long total, int page, int pageSize) {}
    public record Version(String id, int number, long size, String mimeType, String createdAt, String createdByName) {
        public Version(String id, int number, long size, String mimeType, String createdAt) {
            this(id, number, size, mimeType, createdAt, "");
        }
    }
    public record UploadInput(@Positive long spaceId, @PositiveOrZero long parentId,
                              @NotBlank @Size(max=255) String name, @PositiveOrZero @Max(10737418240L) long size,
                              @Size(max=255) String mimeType,
                              @Pattern(regexp="^[0-9a-fA-F]{64}$") String expectedSha256) {
        public UploadInput(long spaceId, long parentId, String name, long size, String mimeType) {
            this(spaceId, parentId, name, size, mimeType, null);
        }
    }
    public record Upload(String id, int chunkSize, int chunkCount, List<Integer> received, List<Integer> missing, String status, String fileId, boolean instantHit, String errorMessage, String uploadMode) {
        public Upload(String id, int chunkSize, int chunkCount, List<Integer> received, List<Integer> missing, String status, String fileId, boolean instantHit, String errorMessage) {
            this(id, chunkSize, chunkCount, received, missing, status, fileId, instantHit, errorMessage, "PROXY");
        }
        public Upload(String id, int chunkSize, int chunkCount, List<Integer> received, List<Integer> missing, String status, String fileId, boolean instantHit) {
            this(id, chunkSize, chunkCount, received, missing, status, fileId, instantHit, null);
        }
        public Upload(String id, int chunkSize, int chunkCount, List<Integer> received, List<Integer> missing, String status, String fileId) {
            this(id, chunkSize, chunkCount, received, missing, status, fileId, false);
        }
    }
    public record Content(java.nio.file.Path path, long size, String name, String mimeType, String etag) {
        public Content(java.nio.file.Path path, long size, String name, String mimeType) {
            this(path, size, name, mimeType, "");
        }
    }
    public record TextChunk(String content, long offset, int bytesRead, long totalBytes, boolean hasMore, boolean binaryDetected) {}
}
