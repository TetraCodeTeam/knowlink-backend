package com.knowlink.api.notifications.listeners;

import com.knowlink.api.bookings.events.BookingCancelledEvent;
import com.knowlink.api.bookings.events.BookingCreatedEvent;
import com.knowlink.api.bookings.events.SessionCompletionNotificationEvent;
import com.knowlink.api.bookings.events.SessionConfirmationReminderEvent;
import com.knowlink.api.bookings.events.SessionNotConfirmedEvent;
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import org.springframework.scheduling.annotation.Async;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingNotificationListener {

    private final INotificationService notificationService;
    private final BookingNotificationContext bookingNotificationContext;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBookingCreated(BookingCreatedEvent event) {
        withSnapshot(event.bookingId(), snapshot -> {
            notifyQuietly(snapshot.studentUserId(), NotificationType.BOOKING_CONFIRMED,
                    "Tu reserva con " + snapshot.tutorName() + " para el " + snapshot.formattedDate()
                            + " a las " + snapshot.formattedTime() + " fue confirmada.",
                    snapshot);
            notifyQuietly(snapshot.tutorUserId(), NotificationType.BOOKING_CONFIRMED,
                    snapshot.studentName() + " reservó una clase para el " + snapshot.formattedDate()
                            + " a las " + snapshot.formattedTime() + ".",
                    snapshot);
        });
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBookingCancelled(BookingCancelledEvent event) {
        withSnapshot(event.bookingId(), snapshot -> {
            if (event.cancelledByUserId() == null) {
                String message = "La clase del " + snapshot.formattedDate() + " a las "
                        + snapshot.formattedTime() + " fue cancelada.";
                notifyQuietly(snapshot.studentUserId(), NotificationType.BOOKING_CANCELLED, message, snapshot);
                notifyQuietly(snapshot.tutorUserId(), NotificationType.BOOKING_CANCELLED, message, snapshot);
                return;
            }

            boolean cancelledByStudent = snapshot.studentUserId().equals(event.cancelledByUserId());
            UUID cancellerUserId = cancelledByStudent ? snapshot.studentUserId() : snapshot.tutorUserId();
            UUID otherPartyUserId = cancelledByStudent ? snapshot.tutorUserId() : snapshot.studentUserId();
            String cancellerName = cancelledByStudent ? snapshot.studentName() : snapshot.tutorName();

            notifyQuietly(cancellerUserId, NotificationType.BOOKING_CANCELLED,
                    "Cancelaste la clase del " + snapshot.formattedDate() + " a las "
                            + snapshot.formattedTime() + ".",
                    snapshot);
            notifyQuietly(otherPartyUserId, NotificationType.BOOKING_CANCELLED,
                    cancellerName + " canceló la clase del " + snapshot.formattedDate() + " a las "
                            + snapshot.formattedTime() + ".",
                    snapshot);
        });
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSessionConfirmationReminder(SessionConfirmationReminderEvent event) {
        withSnapshot(event.booking().getBookingId(), snapshot -> {
            notifyQuietly(snapshot.tutorUserId(), NotificationType.SESSION_CONFIRMATION_REMINDER,
                    "Recordá confirmar la clase con " + snapshot.studentName() + " ingresando su código.",
                    snapshot);
            notifyQuietly(snapshot.studentUserId(), NotificationType.SESSION_CONFIRMATION_REMINDER,
                    "Recordá compartir tu código de confirmación con " + snapshot.tutorName() + ".",
                    snapshot);
        });
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSessionNotConfirmed(SessionNotConfirmedEvent event) {
        withSnapshot(event.booking().getBookingId(), snapshot -> {
            String message = "La clase del " + snapshot.formattedDate() + " no fue confirmada.";
            notifyQuietly(snapshot.studentUserId(), NotificationType.SESSION_NOT_CONFIRMED, message, snapshot);
            notifyQuietly(snapshot.tutorUserId(), NotificationType.SESSION_NOT_CONFIRMED, message, snapshot);
        });
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSessionCompleted(SessionCompletionNotificationEvent event) {
        withSnapshot(event.booking().getBookingId(), snapshot -> {
            String message = "La clase del " + snapshot.formattedDate()
                    + " finalizó. Si hubo algún problema, podés hacer un reclamo.";
            notifyQuietly(snapshot.studentUserId(), NotificationType.SESSION_COMPLETED, message, snapshot);
            notifyQuietly(snapshot.tutorUserId(), NotificationType.SESSION_COMPLETED, message, snapshot);
        });
    }

    private void withSnapshot(UUID bookingId, java.util.function.Consumer<BookingSnapshot> action) {
        try {
            BookingSnapshot snapshot = bookingNotificationContext.loadSnapshot(bookingId)
                    .orElseThrow(() -> new IllegalStateException("Booking not found: " + bookingId));
            action.accept(snapshot);
        } catch (Exception e) {
            log.error("Failed to create booking notifications for booking {}: {}", bookingId, e.getMessage(), e);
        }
    }

    private void notifyQuietly(UUID recipientUserId, NotificationType type, String message, BookingSnapshot snapshot) {
        try {
            notificationService.notifyUser(recipientUserId, type, message, snapshot.bookingId(),
                    snapshot.dedupeKeyFor(type));
        } catch (Exception e) {
            log.error("Failed to create notification {} for user {}: {}", type, recipientUserId, e.getMessage(), e);
        }
    }
}