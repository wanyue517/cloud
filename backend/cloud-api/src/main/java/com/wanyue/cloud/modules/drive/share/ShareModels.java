package com.wanyue.cloud.modules.drive.share;

import jakarta.validation.constraints.*;
import java.util.List;

public final class ShareModels {
    private ShareModels() {}

    public record SpaceMemberItem(
            String id,
            String userId,
            String displayName,
            String email,
            String role,
            String joinedAt,
            String expiresAt) {}

    public record InvitationItem(
            String id,
            String targetType,
            String spaceId,
            String spaceName,
            String nodeId,
            String nodeName,
            String inviterUserId,
            String inviterName,
            String inviteeUserId,
            String inviteeName,
            String role,
            String status,
            String message,
            String createdAt) {}

    public record SpaceMembersOverview(
            String spaceId,
            String kind,
            String myRole,
            List<SpaceMemberItem> members,
            List<InvitationItem> pendingInvitations) {}

    public record InviteMemberInput(
            @NotBlank @Size(max = 128) String target,
            @Pattern(regexp = "MANAGER|EDITOR|VIEWER") String role,
            @Size(max = 255) String message) {}

    public record UpdateMemberRoleInput(
            @Pattern(regexp = "MANAGER|EDITOR|VIEWER") String role) {}

    public record FolderShareInput(
            @NotBlank @Size(max = 128) String target,
            @Pattern(regexp = "VIEWER|EDITOR") String permission) {}

    public record ResourceShareItem(
            String id,
            String spaceId,
            String spaceName,
            String nodeId,
            String nodeName,
            String nodeType,
            String ownerUserId,
            String ownerName,
            String targetUserId,
            String targetName,
            String permission,
            String sourceType,
            String createdAt) {}
}
