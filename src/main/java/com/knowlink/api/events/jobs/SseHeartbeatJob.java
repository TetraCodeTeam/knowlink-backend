package com.knowlink.api.events.jobs;

import com.knowlink.api.events.services.BookingEventPublisher;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SseHeartbeatJob {

    private final BookingEventPublisher eventPublisher;

    @Scheduled(fixedRate = 20_000)
    public void heartbeat() {
        eventPublisher.sendHeartbeat();
    }
}