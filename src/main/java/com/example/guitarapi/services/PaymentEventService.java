package com.example.guitarapi.services;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class PaymentEventService {
    private static final Logger logger = LoggerFactory.getLogger(PaymentEventService.class);
    // Map orderId -> list of emitters
    private final Map<Integer, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(int orderId) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        emitters.computeIfAbsent(orderId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        logger.info("SSE emitter created for orderId={}, totalEmitters={}", orderId, emitters.get(orderId).size());

        emitter.onCompletion(() -> removeEmitter(orderId, emitter));
        emitter.onTimeout(() -> removeEmitter(orderId, emitter));
        emitter.onError((e) -> removeEmitter(orderId, emitter));

        return emitter;
    }

    private void removeEmitter(int orderId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(orderId);
        if (list != null) {
            list.remove(emitter);
            logger.info("SSE emitter removed for orderId={}, remainingEmitters={}", orderId, list.size());
        }
    }

    public void publishEvent(int orderId, String eventName, Object data) {
        List<SseEmitter> list = emitters.get(orderId);
        if (list == null || list.isEmpty()) return;
        logger.info("Publishing SSE event '{}' to orderId={} for {} emitters", eventName, orderId, list.size());
        for (SseEmitter emitter : list) {
            try {
                SseEmitter.SseEventBuilder builder = SseEmitter.event().name(eventName).data(data);
                emitter.send(builder);
                logger.debug("Sent SSE event '{}' to emitter for orderId={}", eventName, orderId);
            } catch (IOException e) {
                // remove dead emitter
                logger.warn("Failed to send SSE to emitter for orderId={} - removing emitter: {}", orderId, e.getMessage());
                removeEmitter(orderId, emitter);
            }
        }
    }

    // Debug helper: return number of active emitters for an orderId
    public int getEmitterCount(int orderId) {
        List<SseEmitter> list = emitters.get(orderId);
        return list == null ? 0 : list.size();
    }
}
