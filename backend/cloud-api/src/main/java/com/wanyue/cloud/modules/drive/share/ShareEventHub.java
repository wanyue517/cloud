package com.wanyue.cloud.modules.drive.share;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class ShareEventHub {
    private final Map<Long, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public SseEmitter subscribe(long userId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        var list = emittersByUser.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable cleanup = () -> {
            list.remove(emitter);
            if (list.isEmpty()) emittersByUser.remove(userId, list);
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

    public void notifyAccessChanged(long userId, String reason, String resourceId) {
        var list = emittersByUser.get(userId);
        if (list == null || list.isEmpty()) return;
        var payload = Map.of("reason", reason, "resourceId", resourceId, "timestamp", System.currentTimeMillis());
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("access-changed").data(payload));
            } catch (IOException e) {
                list.remove(emitter);
            }
        }
    }
}
