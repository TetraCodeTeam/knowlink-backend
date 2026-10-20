package com.knowlink.api.notifications.listeners;

import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.payments.events.FundsTransferProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;
import java.text.NumberFormat;
import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
public class FundsNotificationListener {

    private final INotificationService notificationService;
    private final BookingNotificationContext bookingNotificationContext;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFundsTransferProcessed(FundsTransferProcessedEvent event) {
        try {
            BookingSnapshot snapshot = bookingNotificationContext.loadSnapshot(event.bookingId())
                    .orElseThrow(() -> new IllegalStateException("Booking not found: " + event.bookingId()));

            String amount = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-AR"))
                    .format(event.transferredAmount());
            String message = "Se procesó una transferencia de " + amount + ": " + event.concept() + ".";

            notifyQuietly(snapshot.studentUserId(), message, snapshot);
            notifyQuietly(snapshot.tutorUserId(), message, snapshot);
        } catch (Exception e) {
            log.error("Failed to create funds notifications for booking {}: {}", event.bookingId(),
                    e.getMessage(), e);
        }
    }

    private void notifyQuietly(UUID recipientUserId, String message, BookingSnapshot snapshot) {
        try {
            notificationService.notifyUser(recipientUserId, NotificationType.FUNDS_TRANSFERRED, message,
                    snapshot.bookingId(), snapshot.dedupeKeyFor(NotificationType.FUNDS_TRANSFERRED));
        } catch (Exception e) {
            log.error("Failed to create notification FUNDS_TRANSFERRED for user {}: {}", recipientUserId,
                    e.getMessage(), e);
        }
    }
}