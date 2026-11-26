package com.wanyue.cloud.modules.message;

import com.wanyue.cloud.common.result.R;
import com.wanyue.cloud.common.security.UserIdPrincipal;
import com.wanyue.cloud.modules.message.MessageModels.*;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class MessageController {
    private final MessageService messageService;
    private final MessageEventHub eventHub;

    public MessageController(MessageService messageService, MessageEventHub eventHub) {
        this.messageService = messageService;
        this.eventHub = eventHub;
    }

    private static long user(Authentication auth) {
        return ((UserIdPrincipal) auth.getPrincipal()).userId();
    }

    @GetMapping("/messages")
    public R<InboxResponse> inbox(Authentication auth) {
        return R.ok(messageService.inbox(user(auth)));
    }

    @PutMapping("/messages/{id}/read")
    public R<Void> markRead(Authentication auth, @PathVariable long id) {
        messageService.markRead(user(auth), id);
        return R.ok();
    }

    @PostMapping("/messages/read-all")
    public R<Void> markAllRead(Authentication auth) {
        messageService.markAllRead(user(auth));
        return R.ok();
    }

    @GetMapping(value = "/messages/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(Authentication auth) {
        return eventHub.subscribe(user(auth));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/admin/messages")
    public R<List<AdminMessageRecord>> listAdminMessages() {
        return R.ok(messageService.listAdminMessages());
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/admin/messages")
    public R<String> publishAdminMessage(Authentication auth, @Valid @RequestBody PublishMessageInput input) {
        return R.ok(messageService.publishAdminMessage(user(auth), input));
    }
}
