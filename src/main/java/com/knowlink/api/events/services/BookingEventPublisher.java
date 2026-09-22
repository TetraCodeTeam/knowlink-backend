package com.knowlink.api.events.services;

import com.knowlink.api.events.responses.BookingSlotStatusEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class BookingEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(BookingEventPublisher.class);
    private static final long EMITTER_TIMEOUT = 0L; // sin timeout — se mantiene indefinidamente

    private final Map<UUID, List<SseEmitter>> emittersByTutorProfile = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID tutorProfileId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT);
        List<SseEmitter> emitters = emittersByTutorProfile.computeIfAbsent(
                tutorProfileId, id -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        return emitter;
    }

    public void publish(UUID tutorProfileId, UUID slotId, String status, Instant windowStart, Instant windowEnd) {
        List<SseEmitter> emitters = emittersByTutorProfile.get(tutorProfileId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        BookingSlotStatusEvent payload = new BookingSlotStatusEvent(
                slotId.toString(), status, windowStart, windowEnd);

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().data(payload));
            } catch (Exception e) {
                logger.warn("Failed to send SSE event, removing dead emitter", e);
                emitters.remove(emitter);
            }
        }
    }

    // Mantiene vivas las conexiones ante proxies que cierran conexiones HTTP inactivas.
    public void sendHeartbeat() {
        emittersByTutorProfile.values().forEach(emitters ->
                emitters.removeIf(emitter -> {
                    try {
                        emitter.send(SseEmitter.event().comment("keep-alive"));
                        return false;
                    } catch (Exception e) {
                        return true;
                    }
                }));
    }
}
