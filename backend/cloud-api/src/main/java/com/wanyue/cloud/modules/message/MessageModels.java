package com.wanyue.cloud.modules.message;

import jakarta.validation.constraints.*;
import java.util.List;

public final class MessageModels {
    private MessageModels() {}

    public record MessageItem(
            String id,
            String category,
            String scope,
            String title,
            String content,
            String actionUrl,
            boolean read,
            String createdAt) {}

    public record InboxResponse(long unreadCount, List<MessageItem> items) {}

    public record PublishMessageInput(
            @Pattern(regexp = "ALL|DIRECT") String scope,
            @Size(max = 128) String target,
            @NotBlank @Size(max = 160) String title,
            @Size(max = 2000) String content,
            @Size(max = 255) String actionUrl) {}

    public record AdminMessageRecord(
            String id,
            String scope,
            String category,
            String title,
            String content,
            String actionUrl,
            long recipientCount,
            long readCount,
            String createdAt) {}
}
