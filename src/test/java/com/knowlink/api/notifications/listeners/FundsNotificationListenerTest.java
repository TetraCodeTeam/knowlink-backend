package com.knowlink.api.notifications.listeners;

import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.payments.domain.FundsRecipient;
import com.knowlink.api.payments.events.FundsSuspendedByClaimEvent;
import com.knowlink.api.payments.events.FundsTransferProcessedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundsNotificationListenerTest {

    @Mock
    private INotificationService notificationService;

    @Mock
    private BookingNotificationContext bookingNotificationContext;

    @InjectMocks
    private FundsNotificationListener listener;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID tutorId = UUID.randomUUID();

    @Test
    @DisplayName("Fondos retenidos - solo el alumno recibe el aviso de reserva pagada")
    void heldTransfer_notifiesStudentOnly() {
        stubSnapshot();

        listener.handleFundsTransferProcessed(new FundsTransferProcessedEvent(bookingId, FundsRecipient.TUTOR,
                new BigDecimal("2100.00"), BigDecimal.ZERO, null, "Fondos retenidos para reserva"));

        verify(notificationService).notifyUser(eq(studentId), eq(NotificationType.FUNDS_TRANSFERRED),
                eq("Pago realizado. Reserva realizada"),
                eq("Pagaste $2.100 por la sesión de Sistemas Operativos con Carlos Lopez."
                        + " Los fondos quedarán retenidos hasta la finalización de la sesión."),
                eq(bookingId), eq("FUNDS_TRANSFERRED:Fondos retenidos para reserva:" + bookingId));
        verify(notificationService, never()).notifyUser(eq(tutorId), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Liberacion por sesion completada - solo el tutor recibe el pago con la retencion")
    void releaseCompleted_notifiesTutorOnlyWithRetention() {
        stubSnapshot();

        listener.handleFundsTransferProcessed(new FundsTransferProcessedEvent(bookingId, FundsRecipient.TUTOR,
                new BigDecimal("2100.00"), new BigDecimal("2037.00"), new BigDecimal("3.00"),
                "Liberacion por sesion completada"));

        verify(notificationService).notifyUser(eq(tutorId), eq(NotificationType.FUNDS_TRANSFERRED),
                eq("Pago acreditado. Sesión completada"),
                eq("Recibiste $2.037 por la sesión de Sistemas Operativos con Ana Estudiante."
                        + " (3% de retención aplicada)"),
                eq(bookingId), eq("FUNDS_TRANSFERRED:Liberacion por sesion completada:" + bookingId));
        verify(notificationService, never()).notifyUser(eq(studentId), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Liberacion por alumno ausente - el tutor es informado del credito")
    void releaseAbsentStudent_notifiesTutor() {
        stubSnapshot();

        listener.handleFundsTransferProcessed(new FundsTransferProcessedEvent(bookingId, FundsRecipient.TUTOR,
                new BigDecimal("2100.00"), new BigDecimal("2037.00"), new BigDecimal("3.00"),
                "Liberacion por alumno ausente"));

        verify(notificationService).notifyUser(eq(tutorId), eq(NotificationType.FUNDS_TRANSFERRED),
                eq("Pago acreditado. Alumno ausente"),
                eq("Recibiste $2.037 por la sesión del Martes 20/10."
                        + " El alumno no confirmó asistencia pero cumpliste tu parte."),
                eq(bookingId), eq("FUNDS_TRANSFERRED:Liberacion por alumno ausente:" + bookingId));
    }

    @Test
    @DisplayName("Devolucion por tutor ausente - el alumno es informado del reembolso")
    void refundAbsentTutor_notifiesStudent() {
        stubSnapshot();

        listener.handleFundsTransferProcessed(new FundsTransferProcessedEvent(bookingId, FundsRecipient.STUDENT,
                new BigDecimal("2100.00"), new BigDecimal("2100.00"), null,
                "Devolucion por tutor ausente"));

        verify(notificationService).notifyUser(eq(studentId), eq(NotificationType.FUNDS_TRANSFERRED),
                eq("Pago acreditado. Tutor ausente"),
                eq("Reembolsado $2.100 por la sesión del Martes 20/10."
                        + " El tutor no confirmó asistencia pero cumpliste tu parte."),
                eq(bookingId), eq("FUNDS_TRANSFERRED:Devolucion por tutor ausente:" + bookingId));
    }

    @Test
    @DisplayName("Devolucion por sesion no realizada - el alumno es informado del reembolso")
    void refundNotHeld_notifiesStudent() {
        stubSnapshot();

        listener.handleFundsTransferProcessed(new FundsTransferProcessedEvent(bookingId, FundsRecipient.STUDENT,
                new BigDecimal("2100.00"), new BigDecimal("2100.00"), null,
                "Devolucion por sesion no realizada"));

        verify(notificationService).notifyUser(eq(studentId), eq(NotificationType.FUNDS_TRANSFERRED),
                eq("Pago acreditado. Sesión no realizada"),
                eq("Reembolsado $2.100 por la sesión del Martes 20/10."),
                eq(bookingId), eq("FUNDS_TRANSFERRED:Devolucion por sesion no realizada:" + bookingId));
    }

    @Test
    @DisplayName("Concepto desconocido - ambas partes reciben el mensaje generico")
    void unknownConcept_notifiesBothPartiesWithGenericMessage() {
        stubSnapshot();

        listener.handleFundsTransferProcessed(new FundsTransferProcessedEvent(bookingId, FundsRecipient.TUTOR,
                new BigDecimal("2100.00"), new BigDecimal("2100.00"), null, "concepto raro"));

        verify(notificationService).notifyUser(eq(studentId), eq(NotificationType.FUNDS_TRANSFERRED),
                eq("Pago procesado"),
                eq("Se procesó una transferencia de $2.100: concepto raro."),
                eq(bookingId), eq("FUNDS_TRANSFERRED:concepto raro:" + bookingId));
        verify(notificationService).notifyUser(eq(tutorId), eq(NotificationType.FUNDS_TRANSFERRED),
                eq("Pago procesado"),
                eq("Se procesó una transferencia de $2.100: concepto raro."),
                eq(bookingId), eq("FUNDS_TRANSFERRED:concepto raro:" + bookingId));
    }

    @Test
    @DisplayName("Fondos suspendidos por reclamo - ambas partes reciben FUNDS_SUSPENDED")
    void fundsSuspendedByClaim_notifiesBothParties() {
        stubSnapshot();

        listener.handleFundsSuspendedByClaim(new FundsSuspendedByClaimEvent(bookingId, UUID.randomUUID()));

        verify(notificationService).notifyUser(eq(studentId), eq(NotificationType.FUNDS_SUSPENDED),
                eq("Fondos retenidos. Disputa activa"),
                eq("Los fondos de la sesión con Carlos Lopez están retenidos hasta que se resuelva"
                        + " el reclamo activo."),
                eq(bookingId), eq("FUNDS_SUSPENDED:" + bookingId));
        verify(notificationService).notifyUser(eq(tutorId), eq(NotificationType.FUNDS_SUSPENDED),
                eq("Fondos retenidos. Disputa activa"),
                eq("Los fondos de la sesión con Ana Estudiante están retenidos hasta que se resuelva"
                        + " el reclamo activo."),
                eq(bookingId), eq("FUNDS_SUSPENDED:" + bookingId));
    }

    @Test
    @DisplayName("Fallo al notificar una parte - no se propaga y la otra se notifica igual")
    void failureOnOneRecipient_doesNotPropagate() {
        stubSnapshot();
        doThrow(new RuntimeException("database down"))
                .when(notificationService).notifyUser(eq(studentId), any(NotificationType.class),
                        anyString(), anyString(), eq(bookingId), anyString());

        listener.handleFundsTransferProcessed(new FundsTransferProcessedEvent(bookingId, FundsRecipient.TUTOR,
                new BigDecimal("2100.00"), new BigDecimal("2100.00"), null, "concepto raro"));

        verify(notificationService).notifyUser(eq(tutorId), eq(NotificationType.FUNDS_TRANSFERRED),
                anyString(), anyString(), eq(bookingId), anyString());
    }

    @Test
    @DisplayName("Reserva inexistente - se loguea el error y no se notifica a nadie")
    void missingBooking_isLoggedAndNoNotificationIsCreated() {
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.empty());

        listener.handleFundsTransferProcessed(new FundsTransferProcessedEvent(bookingId, FundsRecipient.TUTOR,
                BigDecimal.TEN, BigDecimal.TEN, null, "concepto"));

        verify(notificationService, never()).notifyUser(any(), any(), any(), any(), any(), any());
    }

    private void stubSnapshot() {
        BookingSnapshot snapshot = new BookingSnapshot(bookingId, studentId, tutorId,
                "Ana Estudiante", "Carlos Lopez", "Sistemas Operativos",
                LocalDate.of(2026, 10, 20), LocalTime.of(15, 0));
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.of(snapshot));
    }
}