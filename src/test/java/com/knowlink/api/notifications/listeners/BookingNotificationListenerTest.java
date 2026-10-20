package com.knowlink.api.notifications.listeners;

import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.events.BookingCancelledEvent;
import com.knowlink.api.bookings.events.BookingCreatedEvent;
import com.knowlink.api.bookings.events.SessionCompletionNotificationEvent;
import com.knowlink.api.bookings.events.SessionConfirmationReminderEvent;
import com.knowlink.api.bookings.events.SessionNotConfirmedEvent;
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class BookingNotificationListenerTest {

    @Mock
    private INotificationService notificationService;

    @Mock
    private BookingNotificationContext bookingNotificationContext;

    @InjectMocks
    private BookingNotificationListener listener;

    private final UUID bookingId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID tutorId = UUID.randomUUID();

    private BookingSnapshot snapshot;

    @BeforeEach
    void setUp() {
        snapshot = new BookingSnapshot(bookingId, studentId, tutorId,
                "Ana Estudiante", "Carlos Lopez",
                LocalDate.of(2026, 10, 20), LocalTime.of(15, 0));
    }

    @Test
    @DisplayName("Reserva creada - alumno y tutor reciben BOOKING_CONFIRMED con sus textos")
    void bookingCreated_notifiesBothParties() {
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.of(snapshot));

        listener.handleBookingCreated(new BookingCreatedEvent(bookingId));

        verify(notificationService).notifyUser(eq(studentId), eq(NotificationType.BOOKING_CONFIRMED),
                eq("Tu reserva con Carlos Lopez para el 20/10/2026 a las 15:00 fue confirmada."),
                eq(bookingId), eq("BOOKING_CONFIRMED:" + bookingId));
        verify(notificationService).notifyUser(eq(tutorId), eq(NotificationType.BOOKING_CONFIRMED),
                eq("Ana Estudiante reservó una clase para el 20/10/2026 a las 15:00."),
                eq(bookingId), eq("BOOKING_CONFIRMED:" + bookingId));
    }

    @Test
    @DisplayName("Reserva cancelada por el alumno - el alumno ve su texto y el tutor el del responsable")
    void bookingCancelledByStudent_notifiesWithProperTexts() {
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.of(snapshot));

        listener.handleBookingCancelled(new BookingCancelledEvent(bookingId, UUID.randomUUID(), tutorId,
                com.knowlink.api.bookings.data.enums.RefundDestination.STUDENT,
                java.math.BigDecimal.TEN, studentId));

        verify(notificationService).notifyUser(eq(studentId), eq(NotificationType.BOOKING_CANCELLED),
                eq("Cancelaste la clase del 20/10/2026 a las 15:00."),
                eq(bookingId), eq("BOOKING_CANCELLED:" + bookingId));
        verify(notificationService).notifyUser(eq(tutorId), eq(NotificationType.BOOKING_CANCELLED),
                eq("Ana Estudiante canceló la clase del 20/10/2026 a las 15:00."),
                eq(bookingId), eq("BOOKING_CANCELLED:" + bookingId));
    }

    @Test
    @DisplayName("Reserva cancelada por el tutor - los textos se intercambian segun el responsable")
    void bookingCancelledByTutor_notifiesWithProperTexts() {
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.of(snapshot));

        listener.handleBookingCancelled(new BookingCancelledEvent(bookingId, UUID.randomUUID(), tutorId,
                com.knowlink.api.bookings.data.enums.RefundDestination.TUTOR,
                java.math.BigDecimal.TEN, tutorId));

        verify(notificationService).notifyUser(eq(tutorId), eq(NotificationType.BOOKING_CANCELLED),
                eq("Cancelaste la clase del 20/10/2026 a las 15:00."),
                eq(bookingId), eq("BOOKING_CANCELLED:" + bookingId));
        verify(notificationService).notifyUser(eq(studentId), eq(NotificationType.BOOKING_CANCELLED),
                eq("Carlos Lopez canceló la clase del 20/10/2026 a las 15:00."),
                eq(bookingId), eq("BOOKING_CANCELLED:" + bookingId));
    }

    @Test
    @DisplayName("Recordatorio de confirmacion - cada parte recibe el texto propio de su rol")
    void sessionConfirmationReminder_notifiesEachRole() {
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.of(snapshot));

        listener.handleSessionConfirmationReminder(new SessionConfirmationReminderEvent(bookingWithId()));

        verify(notificationService).notifyUser(eq(tutorId), eq(NotificationType.SESSION_CONFIRMATION_REMINDER),
                eq("Recordá confirmar la clase con Ana Estudiante ingresando su código."),
                eq(bookingId), eq("SESSION_CONFIRMATION_REMINDER:" + bookingId));
        verify(notificationService).notifyUser(eq(studentId), eq(NotificationType.SESSION_CONFIRMATION_REMINDER),
                eq("Recordá compartir tu código de confirmación con Carlos Lopez."),
                eq(bookingId), eq("SESSION_CONFIRMATION_REMINDER:" + bookingId));
    }

    @Test
    @DisplayName("Sesion no confirmada - ambas partes reciben el mismo aviso")
    void sessionNotConfirmed_notifiesBothParties() {
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.of(snapshot));

        listener.handleSessionNotConfirmed(new SessionNotConfirmedEvent(bookingWithId()));

        String expected = "La clase del 20/10/2026 no fue confirmada.";
        verify(notificationService).notifyUser(eq(studentId), eq(NotificationType.SESSION_NOT_CONFIRMED),
                eq(expected), eq(bookingId), eq("SESSION_NOT_CONFIRMED:" + bookingId));
        verify(notificationService).notifyUser(eq(tutorId), eq(NotificationType.SESSION_NOT_CONFIRMED),
                eq(expected), eq(bookingId), eq("SESSION_NOT_CONFIRMED:" + bookingId));
    }

    @Test
    @DisplayName("Sesion finalizada - ambas partes reciben el aviso de cierre")
    void sessionCompleted_notifiesBothParties() {
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.of(snapshot));

        listener.handleSessionCompleted(new SessionCompletionNotificationEvent(bookingWithId()));

        String expected = "La clase del 20/10/2026 finalizó. Si hubo algún problema, podés hacer un reclamo.";
        verify(notificationService).notifyUser(eq(studentId), eq(NotificationType.SESSION_COMPLETED),
                eq(expected), eq(bookingId), eq("SESSION_COMPLETED:" + bookingId));
        verify(notificationService).notifyUser(eq(tutorId), eq(NotificationType.SESSION_COMPLETED),
                eq(expected), eq(bookingId), eq("SESSION_COMPLETED:" + bookingId));
    }

    @Test
    @DisplayName("Fallo al notificar a una parte - no se propaga y la otra parte igual se notifica")
    void failureOnOneRecipient_doesNotPropagateAndStillNotifiesTheOther() {
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.of(snapshot));
        doThrow(new RuntimeException("database down"))
                .when(notificationService).notifyUser(eq(studentId), eq(NotificationType.BOOKING_CONFIRMED),
                        anyString(), eq(bookingId), anyString());

        listener.handleBookingCreated(new BookingCreatedEvent(bookingId));

        verify(notificationService).notifyUser(eq(tutorId), eq(NotificationType.BOOKING_CONFIRMED),
                anyString(), eq(bookingId), anyString());
    }

    @Test
    @DisplayName("Reserva inexistente - se loguea el error y no se notifica a nadie")
    void missingBooking_isLoggedAndNoNotificationIsCreated() {
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.empty());

        listener.handleBookingCreated(new BookingCreatedEvent(bookingId));

        verify(notificationService, never()).notifyUser(any(), any(), any(), any(), any());
    }

    private Booking bookingWithId() {
        return Booking.builder().bookingId(bookingId).build();
    }
}