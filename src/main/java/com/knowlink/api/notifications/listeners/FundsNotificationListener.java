package com.knowlink.api.notifications.listeners;

import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.notifications.utils.NotificationTextFormats;
import com.knowlink.api.payments.events.FundsSuspendedByClaimEvent;
import com.knowlink.api.payments.events.FundsTransferProcessedEvent;
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

    private static final String CONCEPT_HELD = "Fondos retenidos para reserva";
    private static final String CONCEPT_RELEASE_COMPLETED = "Liberacion por sesion completada";
    private static final String CONCEPT_RELEASE_ABSENT_STUDENT = "Liberacion por alumno ausente";
    private static final String CONCEPT_REFUND_ABSENT_TUTOR = "Devolucion por tutor ausente";
    private static final String CONCEPT_REFUND_NOT_HELD = "Devolucion por sesion no realizada";

    private final INotificationService notificationService;
    private final BookingNotificationContext bookingNotificationContext;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleFundsTransferProcessed(FundsTransferProcessedEvent event) {
        try {
            BookingSnapshot snapshot = bookingNotificationContext.loadSnapshot(event.bookingId())
                    .orElseThrow(() -> new IllegalStateException("Booking not found: " + event.bookingId()));

            switch (event.concept()) {
                case CONCEPT_HELD -> notifyQuietly(snapshot.studentUserId(), NotificationType.FUNDS_TRANSFERRED,
                        "Pago realizado. Reserva realizada",
                        "Pagaste " + NotificationTextFormats.amount(event.originalAmount()) + " por la sesión de "
                                + snapshot.subjectName() + " con " + snapshot.tutorName()
                                + ". Los fondos quedarán retenidos hasta la finalización de la sesión.",
                        event);
                case CONCEPT_RELEASE_COMPLETED -> notifyQuietly(snapshot.tutorUserId(),
                        NotificationType.FUNDS_TRANSFERRED,
                        "Pago acreditado. Sesión completada",
                        "Recibiste " + NotificationTextFormats.amount(event.transferredAmount())
                                + " por la sesión de " + snapshot.subjectName() + " con " + snapshot.studentName()
                                + ". (" + retentionLabel(event) + "% de retención aplicada)",
                        event);
                case CONCEPT_RELEASE_ABSENT_STUDENT -> notifyQuietly(snapshot.tutorUserId(),
                        NotificationType.FUNDS_TRANSFERRED,
                        "Pago acreditado. Alumno ausente",
                        "Recibiste " + NotificationTextFormats.amount(event.transferredAmount())
                                + " por la sesión del " + snapshot.formattedDateWithWeekday()
                                + ". El alumno no confirmó asistencia pero cumpliste tu parte.",
                        event);
                case CONCEPT_REFUND_ABSENT_TUTOR -> notifyQuietly(snapshot.studentUserId(),
                        NotificationType.FUNDS_TRANSFERRED,
                        "Pago acreditado. Tutor ausente",
                        "Reembolsado " + NotificationTextFormats.amount(event.transferredAmount())
                                + " por la sesión del " + snapshot.formattedDateWithWeekday()
                                + ". El tutor no confirmó asistencia pero cumpliste tu parte.",
                        event);
                case CONCEPT_REFUND_NOT_HELD -> notifyQuietly(snapshot.studentUserId(),
                        NotificationType.FUNDS_TRANSFERRED,
                        "Pago acreditado. Sesión no realizada",
                        "Reembolsado " + NotificationTextFormats.amount(event.transferredAmount())
                                + " por la sesión del " + snapshot.formattedDateWithWeekday() + ".",
                        event);
                default -> {
                    String message = "Se procesó una transferencia de "
                            + NotificationTextFormats.amount(event.transferredAmount()) + ": " + event.concept()
                            + ".";
                    notifyQuietly(snapshot.studentUserId(), NotificationType.FUNDS_TRANSFERRED,
                            "Pago procesado", message, event);
                    notifyQuietly(snapshot.tutorUserId(), NotificationType.FUNDS_TRANSFERRED,
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
            notifyQuietly(snapshot.studentUserId(), NotificationType.FUNDS_SUSPENDED, title,
                    "Los fondos de la sesión con " + snapshot.tutorName()
                            + " están retenidos hasta que se resuelva el reclamo activo.",
                    event.bookingId(), dedupeKey);
            notifyQuietly(snapshot.tutorUserId(), NotificationType.FUNDS_SUSPENDED, title,
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

    private void notifyQuietly(UUID recipientUserId, NotificationType type, String title, String message,
                               UUID bookingId, String dedupeKey) {
        try {
            notificationService.notifyUser(recipientUserId, type, title, message, bookingId, dedupeKey);
        } catch (Exception e) {
            log.error("Failed to create notification {} for user {}: {}", type, recipientUserId, e.getMessage(), e);
        }
    }

    private void notifyQuietly(UUID recipientUserId, NotificationType type, String title, String message,
                               FundsTransferProcessedEvent event) {
        String dedupeKey = type.name() + ":" + event.concept() + ":" + event.bookingId();
        notifyQuietly(recipientUserId, type, title, message, event.bookingId(), dedupeKey);
    }
}