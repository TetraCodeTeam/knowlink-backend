package com.knowlink.api.notifications.listeners;

import com.knowlink.api.bookings.data.enums.RefundDestination;
import com.knowlink.api.bookings.events.BookingCancelledEvent;
import com.knowlink.api.bookings.events.BookingCreatedEvent;
import com.knowlink.api.bookings.events.SessionCompletionNotificationEvent;
import com.knowlink.api.bookings.events.SessionConfirmationReminderEvent;
import com.knowlink.api.bookings.events.SessionConfirmedEvent;
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
import java.util.function.Consumer;

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
                    "Reserva confirmada",
                    "Reservaste " + snapshot.subjectName() + " - " + snapshot.formattedDateWithWeekday()
                            + " a las " + snapshot.formattedTime() + ".",
                    snapshot);
            notifyQuietly(snapshot.tutorUserId(), NotificationType.BOOKING_CONFIRMED,
                    "Nueva reserva confirmada",
                    snapshot.studentName() + " reservó " + snapshot.subjectName() + " - "
                            + snapshot.formattedDateWithWeekday() + " a las " + snapshot.formattedTime() + ".",
                    snapshot);
        });
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBookingCancelled(BookingCancelledEvent event) {
        withSnapshot(event.bookingId(), snapshot -> {
            if (event.cancelledByUserId() == null) {
                String message = "La clase del " + snapshot.formattedDateWithWeekday() + " a las "
                        + snapshot.formattedTime() + " fue cancelada.";
                notifyQuietly(snapshot.studentUserId(), NotificationType.BOOKING_CANCELLED,
                        "Clase cancelada", message, snapshot);
                notifyQuietly(snapshot.tutorUserId(), NotificationType.BOOKING_CANCELLED,
                        "Clase cancelada", message, snapshot);
                return;
            }

            boolean cancelledByStudent = snapshot.studentUserId().equals(event.cancelledByUserId());
            UUID cancellerUserId = cancelledByStudent ? snapshot.studentUserId() : snapshot.tutorUserId();
            UUID otherPartyUserId = cancelledByStudent ? snapshot.tutorUserId() : snapshot.studentUserId();
            String cancellerName = cancelledByStudent ? snapshot.studentName() : snapshot.tutorName();
            String otherPartyTitle = cancelledByStudent
                    ? "Clase cancelada por el alumno"
                    : "Clase cancelada por el tutor";
            String otherPartyMessage = cancellerName + " canceló la clase del "
                    + snapshot.formattedDateWithWeekday() + "." + refundNotice(event.refundDestination());

            notifyQuietly(cancellerUserId, NotificationType.BOOKING_CANCELLED,
                    "Clase cancelada",
                    "Cancelaste la clase del " + snapshot.formattedDateWithWeekday() + ".",
                    snapshot);
            notifyQuietly(otherPartyUserId, NotificationType.BOOKING_CANCELLED,
                    otherPartyTitle, otherPartyMessage, snapshot);
        });
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSessionConfirmationReminder(SessionConfirmationReminderEvent event) {
        withSnapshot(event.booking().getBookingId(), snapshot -> {
            notifyQuietly(snapshot.tutorUserId(), NotificationType.SESSION_CONFIRMATION_REMINDER,
                    "Recordatorio de token. Clase en curso",
                    "Tu clase de " + snapshot.subjectName() + " con " + snapshot.studentName()
                            + " está en curso. Ingresá el código para confirmar la sesión.",
                    snapshot);
            notifyQuietly(snapshot.studentUserId(), NotificationType.SESSION_CONFIRMATION_REMINDER,
                    "Recordatorio de token. Clase en curso",
                    "Tu clase de " + snapshot.subjectName() + " con " + snapshot.tutorName()
                            + " está en curso. Entregá el código al tutor para confirmar la sesión.",
                    snapshot);
        });
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSessionNotConfirmed(SessionNotConfirmedEvent event) {
        withSnapshot(event.booking().getBookingId(), snapshot -> {
            String title = "Token vencido. Sesión no confirmada";
            notifyQuietly(snapshot.studentUserId(), NotificationType.SESSION_NOT_CONFIRMED, title,
                    "El tiempo para ingresar el código de la sesión con " + snapshot.tutorName()
                            + " venció. La reserva pasó al estado \"No confirmada\".",
                    snapshot);
            notifyQuietly(snapshot.tutorUserId(), NotificationType.SESSION_NOT_CONFIRMED, title,
                    "El tiempo para ingresar el código de la sesión con " + snapshot.studentName()
                            + " venció. La reserva pasó al estado \"No confirmada\".",
                    snapshot);
        });
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSessionConfirmed(SessionConfirmedEvent event) {
        withSnapshot(event.booking().getBookingId(), snapshot -> {
            String title = "Sesión confirmada";
            notifyQuietly(snapshot.studentUserId(), NotificationType.SESSION_CONFIRMED, title,
                    "La sesión de " + snapshot.subjectName() + " con " + snapshot.tutorName()
                            + " fue confirmada.",
                    snapshot);
            notifyQuietly(snapshot.tutorUserId(), NotificationType.SESSION_CONFIRMED, title,
                    "La sesión de " + snapshot.subjectName() + " con " + snapshot.studentName()
                            + " fue confirmada.",
                    snapshot);
        });
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSessionCompleted(SessionCompletionNotificationEvent event) {
        withSnapshot(event.booking().getBookingId(), snapshot -> {
            String title = "Tu sesión de " + snapshot.subjectName() + " finalizó";
            String message = "Si hubo algún inconveniente con la asistencia, tenés 24 horas para reclamar.";
            notifyQuietly(snapshot.studentUserId(), NotificationType.SESSION_COMPLETED, title, message, snapshot);
            notifyQuietly(snapshot.tutorUserId(), NotificationType.SESSION_COMPLETED, title, message, snapshot);

            String ratingTitle = "Calificá tu sesión de " + snapshot.subjectName();
            notifyQuietly(snapshot.studentUserId(), NotificationType.RATING_ENABLED, ratingTitle,
                    "Contanos cómo fue tu experiencia con " + snapshot.tutorName()
                            + ". Tenés 24 horas para calificar.",
                    snapshot);
            notifyQuietly(snapshot.tutorUserId(), NotificationType.RATING_ENABLED, ratingTitle,
                    "Contanos cómo fue tu experiencia con " + snapshot.studentName()
                            + ". Tenés 24 horas para calificar.",
                    snapshot);
        });
    }

    private static String refundNotice(RefundDestination refundDestination) {
        if (refundDestination == RefundDestination.STUDENT) {
            return " El alumno recibió el reembolso por política de cancelación.";
        }
        if (refundDestination == RefundDestination.TUTOR) {
            return " El tutor recibirá el pago por la política de cancelación.";
        }
        return "";
    }

    private void withSnapshot(UUID bookingId, Consumer<BookingSnapshot> action) {
        try {
            BookingSnapshot snapshot = bookingNotificationContext.loadSnapshot(bookingId)
                    .orElseThrow(() -> new IllegalStateException("Booking not found: " + bookingId));
            action.accept(snapshot);
        } catch (Exception e) {
            log.error("Failed to create booking notifications for booking {}: {}", bookingId, e.getMessage(), e);
        }
    }

    private void notifyQuietly(UUID recipientUserId, NotificationType type, String title, String message,
                               BookingSnapshot snapshot) {
        try {
            notificationService.notifyUser(recipientUserId, type, title, message, snapshot.bookingId(),
                    snapshot.dedupeKeyFor(type));
        } catch (Exception e) {
            log.error("Failed to create notification {} for user {}: {}", type, recipientUserId, e.getMessage(), e);
        }
    }
}