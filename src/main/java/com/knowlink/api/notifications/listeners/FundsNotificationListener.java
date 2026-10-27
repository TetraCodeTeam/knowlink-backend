package com.knowlink.api.notifications.listeners;

import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.notifications.utils.NotificationTextFormats;
import com.knowlink.api.payments.events.FundsSuspendedByClaimEvent;
import com.knowlink.api.payments.events.FundsTransferProcessedEvent;
import com.knowlink.api.security.enums.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

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

            switch (event.concept()) {
                case HELD -> notifyQuietly(snapshot.studentUserId(), Role.STUDENT,
                        NotificationType.FUNDS_TRANSFERRED,
                        "Pago realizado. Reserva realizada",
                        "Pagaste " + NotificationTextFormats.amount(event.originalAmount()) + " por la sesión de "
                                + snapshot.subjectName() + " con " + snapshot.tutorName()
                                + ". Los fondos quedarán retenidos hasta la finalización de la sesión.",
                        event);
                case RELEASED_SESSION_COMPLETED -> notifyQuietly(snapshot.tutorUserId(), Role.TUTOR,
                        NotificationType.FUNDS_TRANSFERRED,
                        "Pago acreditado. Sesión completada",
                        "Recibiste " + NotificationTextFormats.amount(event.transferredAmount())
                                + " por la sesión de " + snapshot.subjectName() + " con " + snapshot.studentName()
                                + ". (" + retentionLabel(event) + "% de retención aplicada)",
                        event);
                case RELEASED_STUDENT_ABSENT -> notifyQuietly(snapshot.tutorUserId(), Role.TUTOR,
                        NotificationType.FUNDS_TRANSFERRED,
                        "Pago acreditado. Alumno ausente",
                        "Recibiste " + NotificationTextFormats.amount(event.transferredAmount())
                                + " por la sesión del " + snapshot.formattedDateWithWeekday()
                                + ". El alumno no confirmó asistencia pero cumpliste tu parte.",
                        event);
                case REFUNDED_TUTOR_ABSENT -> notifyQuietly(snapshot.studentUserId(), Role.STUDENT,
                        NotificationType.FUNDS_TRANSFERRED,
                        "Pago acreditado. Tutor ausente",
                        "Reembolsado " + NotificationTextFormats.amount(event.transferredAmount())
                                + " por la sesión del " + snapshot.formattedDateWithWeekday()
                                + ". El tutor no confirmó asistencia pero cumpliste tu parte.",
                        event);
                case REFUNDED_SESSION_NOT_HELD -> notifyQuietly(snapshot.studentUserId(), Role.STUDENT,
                        NotificationType.FUNDS_TRANSFERRED,
                        "Pago acreditado. Sesión no realizada",
                        "Reembolsado " + NotificationTextFormats.amount(event.transferredAmount())
                                + " por la sesión del " + snapshot.formattedDateWithWeekday() + ".",
                        event);
                default -> {
                    String message = "Se procesó una transferencia de "
                            + NotificationTextFormats.amount(event.transferredAmount()) + ": " + event.concept().label()
                            + ".";
                    notifyQuietly(snapshot.studentUserId(), Role.STUDENT, NotificationType.FUNDS_TRANSFERRED,
                            "Pago procesado", message, event);
                    notifyQuietly(snapshot.tutorUserId(), Role.TUTOR, NotificationType.FUNDS_TRANSFERRED,
                            "Pago procesado", message, event);
                }
            }
        } catch (Exception e) {
            log.error("Failed to create funds notifications for booking {}: {}", event.bookingId(),
                    e.getMessage(), e);
        }
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFundsSuspendedByClaim(FundsSuspendedByClaimEvent event) {
        try {
            BookingSnapshot snapshot = bookingNotificationContext.loadSnapshot(event.bookingId())
                    .orElseThrow(() -> new IllegalStateException("Booking not found: " + event.bookingId()));

            String title = "Fondos retenidos. Disputa activa";
            String dedupeKey = NotificationType.FUNDS_SUSPENDED.name() + ":" + event.bookingId();
            notifyQuietly(snapshot.studentUserId(), Role.STUDENT, NotificationType.FUNDS_SUSPENDED, title,
                    "Los fondos de la sesión con " + snapshot.tutorName()
                            + " están retenidos hasta que se resuelva el reclamo activo.",
                    event.bookingId(), dedupeKey);
            notifyQuietly(snapshot.tutorUserId(), Role.TUTOR, NotificationType.FUNDS_SUSPENDED, title,
                    "Los fondos de la sesión con " + snapshot.studentName()
                            + " están retenidos hasta que se resuelva el reclamo activo.",
                    event.bookingId(), dedupeKey);
        } catch (Exception e) {
            log.error("Failed to create funds suspension notifications for booking {}: {}", event.bookingId(),
                    e.getMessage(), e);
        }
    }

    private static String retentionLabel(FundsTransferProcessedEvent event) {
        if (event.retentionPercentage() == null) {
            return "0";
        }
        return event.retentionPercentage().stripTrailingZeros().toPlainString();
    }

    private void notifyQuietly(UUID recipientUserId, Role recipientRole, NotificationType type, String title,
                               String message, UUID bookingId, String dedupeKey) {
        try {
            notificationService.notifyUser(recipientUserId, recipientRole, type, title, message, bookingId, dedupeKey);
        } catch (Exception e) {
            log.error("Failed to create notification {} for user {}: {}", type, recipientUserId, e.getMessage(), e);
        }
    }

    private void notifyQuietly(UUID recipientUserId, Role recipientRole, NotificationType type, String title,
                               String message, FundsTransferProcessedEvent event) {
        String dedupeKey = type.name() + ":" + event.concept().label() + ":" + event.bookingId();
        notifyQuietly(recipientUserId, recipientRole, type, title, message, event.bookingId(), dedupeKey);
    }
}