package com.wanyue.cloud.modules.message;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class MessageEventHub {
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(long userId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        var list = emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable cleanup = () -> {
            list.remove(emitter);
            if (list.isEmpty()) emitters.remove(userId, list);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("ok", true)));
        } catch (IOException e) {
            cleanup.run();
        }
        return emitter;
    }

    public void pushNewMessage(long userId, String messageId, String title, String category, String actionUrl) {
        var list = emitters.get(userId);
        if (list == null || list.isEmpty()) return;
        var payload = Map.of(
                "id", messageId,
                "title", title,
                "category", category,
                "actionUrl", actionUrl == null ? "" : actionUrl,
                "timestamp", System.currentTimeMillis());
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("new-message").data(payload));
            } catch (IOException e) {
                list.remove(emitter);
            }
        }
    }
}
