package com.knowlink.api.notifications.listeners;

import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.payments.domain.FundsRecipient;
import com.knowlink.api.payments.events.FundsTransferProcessedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("Transferencia procesada - ambas partes reciben FUNDS_TRANSFERRED con monto y concepto")
    void fundsTransferProcessed_notifiesBothParties() {
        stubSnapshot();

        listener.handleFundsTransferProcessed(new FundsTransferProcessedEvent(bookingId, FundsRecipient.TUTOR,
                new BigDecimal("2037.00"), "Liberacion por sesion completada"));

        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyUser(eq(studentId), eq(NotificationType.FUNDS_TRANSFERRED),
                messages.capture(), eq(bookingId), eq("FUNDS_TRANSFERRED:" + bookingId));
        verify(notificationService).notifyUser(eq(tutorId), eq(NotificationType.FUNDS_TRANSFERRED),
                messages.capture(), eq(bookingId), eq("FUNDS_TRANSFERRED:" + bookingId));

        assertThat(messages.getAllValues()).hasSize(2).allSatisfy(message -> {
            assertThat(message).startsWith("Se procesó una transferencia de ");
            assertThat(message).contains("Liberacion por sesion completada");
            assertThat(message).doesNotContain("token");
        });
    }

    @Test
    @DisplayName("Fallo al notificar una parte - no se propaga y la otra se notifica igual")
    void failureOnOneRecipient_doesNotPropagate() {
        stubSnapshot();
        doThrow(new RuntimeException("database down"))
                .when(notificationService).notifyUser(eq(studentId), any(NotificationType.class),
                        anyString(), eq(bookingId), anyString());

        listener.handleFundsTransferProcessed(new FundsTransferProcessedEvent(bookingId, FundsRecipient.STUDENT,
                new BigDecimal("2100.00"), "Devolucion por sesion no realizada"));

        verify(notificationService).notifyUser(eq(tutorId), eq(NotificationType.FUNDS_TRANSFERRED),
                anyString(), eq(bookingId), anyString());
    }

    @Test
    @DisplayName("Reserva inexistente - se loguea el error y no se notifica a nadie")
    void missingBooking_isLoggedAndNoNotificationIsCreated() {
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.empty());

        listener.handleFundsTransferProcessed(new FundsTransferProcessedEvent(bookingId, FundsRecipient.TUTOR,
                BigDecimal.TEN, "concepto"));

        verify(notificationService, never()).notifyUser(any(), any(), any(), any(), any());
    }

    private void stubSnapshot() {
        BookingSnapshot snapshot = new BookingSnapshot(bookingId, studentId, tutorId,
                "Ana Estudiante", "Carlos Lopez",
                LocalDate.of(2026, 10, 20), LocalTime.of(15, 0));
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.of(snapshot));
    }
}