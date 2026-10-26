package com.knowlink.api.notifications.listeners;

import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.ratings.events.RatingSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class RatingNotificationListener {

    private final INotificationService notificationService;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleRatingSubmitted(RatingSubmittedEvent event) {
        try {
            notificationService.resolveNotification(
                    event.raterUserId(), NotificationType.RATING_ENABLED, event.bookingId());
        } catch (Exception e) {
            log.error("Failed to resolve rating notification for booking {}: {}",
                    event.bookingId(), e.getMessage(), e);
        }
    }
}