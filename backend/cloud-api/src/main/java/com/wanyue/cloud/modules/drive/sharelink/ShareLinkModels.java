package com.wanyue.cloud.modules.drive.sharelink;

import jakarta.validation.constraints.*;
import java.util.List;

public final class ShareLinkModels {
    private ShareLinkModels() {}

    public record CreateShareLinkInput(
            @Size(max = 16) String password,
            Integer expireDays,
            Boolean allowDownload,
            @Min(1) @Max(100000) Integer maxDownloads) {}

    public record CreatedShareLink(
            String id,
            String token,
            String urlPath,
            boolean hasPassword,
            boolean allowDownload,
            Integer maxDownloads,
            String expiresAt) {}

    public record ShareLinkItem(
            String id,
            String spaceId,
            String nodeId,
            String nodeName,
            String nodeType,
            String tokenPrefix,
            boolean hasPassword,
            boolean allowDownload,
            Integer maxDownloads,
            int downloadCount,
            int viewCount,
            String expiresAt,
            String disabledAt,
            String createdAt) {}

    public record PublicShareMeta(
            String nodeId,
            String nodeName,
            String nodeType,
            long size,
            String mimeType,
            String ownerName,
            boolean requiresPassword,
            boolean unlocked,
            boolean allowDownload,
            String expiresAt) {}

    public record VerifyPasswordInput(@NotBlank @Size(max = 32) String password) {}

    public record PublicNode(String id, String parentId, String name, String nodeType, long size, String mimeType, String updatedAt) {}

    public record PublicNodeList(List<PublicNode> items) {}

    public record SavePublicShareInput(
            @NotNull Long targetSpaceId,
            @Min(0) long targetParentId,
            Long nodeId,
            @Size(max = 32) String password) {}
}
