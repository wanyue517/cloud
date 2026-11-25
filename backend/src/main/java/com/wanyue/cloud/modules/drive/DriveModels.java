package com.wanyue.cloud.modules.drive;

import jakarta.validation.constraints.*;
import java.util.List;

public final class DriveModels {
    private DriveModels() {}
    public record Space(String id, String name, String kind, String color, long sortOrder) {}
    public record SpaceInput(@NotBlank @Size(max=50) String name, @Size(max=500) String description,
                             @Pattern(regexp="#[0-9a-fA-F]{6}") String color, Long sortOrder) {}
    public record Node(String id, String spaceId, String parentId, String name, String nodeType,
                       String currentVersionId, long revision, long size, String mimeType,
                       boolean hidden, String updatedAt, boolean hasChildren) {}
    public record NodeInput(@NotBlank @Size(max=255) String name, @PositiveOrZero long parentId) {}
    public record ChangeInput(@NotBlank @Size(max=255) String name, @PositiveOrZero long parentId,
                              @PositiveOrZero long revision) {}
    public record Page(List<Node> items, long total, int page, int pageSize) {}
    public record Version(String id, int number, long size, String mimeType, String createdAt) {}
    public record UploadInput(@Positive long spaceId, @PositiveOrZero long parentId,
                              @NotBlank @Size(max=255) String name, @PositiveOrZero @Max(10737418240L) long size,
                              @Size(max=255) String mimeType) {}
    public record Upload(String id, int chunkSize, int chunkCount, List<Integer> received, String status, String fileId) {}
    public record Content(java.nio.file.Path path, long size, String name, String mimeType) {}
}
