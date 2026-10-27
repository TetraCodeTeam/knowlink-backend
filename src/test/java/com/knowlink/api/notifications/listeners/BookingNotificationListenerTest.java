package com.knowlink.api.notifications.listeners;

import com.knowlink.api.bookings.data.enums.RefundDestination;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.events.BookingCancelledEvent;
import com.knowlink.api.bookings.events.BookingCreatedEvent;
import com.knowlink.api.bookings.events.SessionCompletionNotificationEvent;
import com.knowlink.api.bookings.events.SessionConfirmationReminderEvent;
import com.knowlink.api.bookings.events.SessionConfirmationTokenGeneratedEvent;
import com.knowlink.api.bookings.events.SessionConfirmedEvent;
import com.knowlink.api.bookings.events.SessionNotConfirmedEvent;
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.security.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
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
    private static final LocalDateTime RATING_EXPIRES_AT = LocalDateTime.of(2026, 10, 21, 16, 0);

    private BookingSnapshot snapshot;

    @BeforeEach
    void setUp() {
        snapshot = new BookingSnapshot(bookingId, studentId, tutorId,
                "Ana Estudiante", "Carlos Lopez", "Sistemas Operativos",
                LocalDate.of(2026, 10, 20), LocalTime.of(15, 0), LocalTime.of(16, 0),
                LocalDateTime.of(2026, 10, 20, 15, 30));
    }

    @Test
    @DisplayName("Reserva creada - alumno y tutor reciben BOOKING_CONFIRMED con sus textos")
    void bookingCreated_notifiesBothParties() {
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.of(snapshot));

        listener.handleBookingCreated(new BookingCreatedEvent(bookingId));

        verify(notificationService).notifyUser(eq(studentId), eq(Role.STUDENT), eq(NotificationType.BOOKING_CONFIRMED),
                eq("Reserva confirmada"),
                eq("Reservaste Sistemas Operativos - Martes 20/10 a las 15:00."),
                eq(bookingId), eq("BOOKING_CONFIRMED:" + bookingId));
        verify(notificationService).notifyUser(eq(tutorId), eq(Role.TUTOR), eq(NotificationType.BOOKING_CONFIRMED),
                eq("Nueva reserva confirmada"),
                eq("Ana Estudiante reservó Sistemas Operativos - Martes 20/10 a las 15:00."),
                eq(bookingId), eq("BOOKING_CONFIRMED:" + bookingId));
    }

    @Test
    @DisplayName("Reserva cancelada por el alumno - el alumno ve su texto y el tutor el del responsable")
    void bookingCancelledByStudent_notifiesWithProperTexts() {
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.of(snapshot));

        listener.handleBookingCancelled(new BookingCancelledEvent(bookingId, UUID.randomUUID(), tutorId,
                RefundDestination.STUDENT, BigDecimal.TEN, studentId));

        verify(notificationService).notifyUser(eq(studentId), eq(Role.STUDENT), eq(NotificationType.BOOKING_CANCELLED),
                eq("Clase cancelada"),
                eq("Cancelaste la clase del Martes 20/10."),
                eq(bookingId), eq("BOOKING_CANCELLED:" + bookingId));
        verify(notificationService).notifyUser(eq(tutorId), eq(Role.TUTOR), eq(NotificationType.BOOKING_CANCELLED),
                eq("Clase cancelada por el alumno"),
                eq("Ana Estudiante canceló la clase del Martes 20/10."
                        + " El alumno recibió el reembolso por política de cancelación."),
                eq(bookingId), eq("BOOKING_CANCELLED:" + bookingId));
    }

    @Test
    @DisplayName("Reserva cancelada por el tutor - los textos se intercambian segun el responsable")
    void bookingCancelledByTutor_notifiesWithProperTexts() {
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.of(snapshot));

        listener.handleBookingCancelled(new BookingCancelledEvent(bookingId, UUID.randomUUID(), tutorId,
                RefundDestination.TUTOR, BigDecimal.TEN, tutorId));

        verify(notificationService).notifyUser(eq(tutorId), eq(Role.TUTOR), eq(NotificationType.BOOKING_CANCELLED),
                eq("Clase cancelada"),
                eq("Cancelaste la clase del Martes 20/10."),
                eq(bookingId), eq("BOOKING_CANCELLED:" + bookingId));
        verify(notificationService).notifyUser(eq(studentId), eq(Role.STUDENT), eq(NotificationType.BOOKING_CANCELLED),
                eq("Clase cancelada por el tutor"),
                eq("Carlos Lopez canceló la clase del Martes 20/10."
                        + " El tutor recibirá el pago por la política de cancelación."),
                eq(bookingId), eq("BOOKING_CANCELLED:" + bookingId));
    }

    @Test
    @DisplayName("Recordatorio de confirmacion - cada parte recibe el texto propio de su rol")
    void sessionConfirmationReminder_notifiesEachRole() {
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.of(snapshot));

        LocalDateTime reminderAt = LocalDateTime.of(2026, 10, 20, 15, 10);

        listener.handleSessionConfirmationReminder(new SessionConfirmationReminderEvent(bookingWithId(), reminderAt));

        String dedupeKey = "SESSION_CONFIRMATION_REMINDER:" + bookingId + ":" + reminderAt;
        verify(notificationService).notifyUser(eq(tutorId), eq(Role.TUTOR), eq(NotificationType.SESSION_CONFIRMATION_REMINDER),
                eq("Recordatorio de token. Clase en curso"),
                eq("Tu clase de Sistemas Operativos con Ana Estudiante está en curso."
                        + " Ingresá el código para confirmar la sesión."),
                eq(bookingId), eq(dedupeKey));
        verify(notificationService).notifyUser(eq(studentId), eq(Role.STUDENT), eq(NotificationType.SESSION_CONFIRMATION_REMINDER),
                eq("Recordatorio de token. Clase en curso"),
                eq("Tu clase de Sistemas Operativos con Carlos Lopez está en curso."
                        + " Entregá el código al tutor para confirmar la sesión."),
                eq(bookingId), eq(dedupeKey));
    }

    @Test
    @DisplayName("Recordatorio cada 10 minutos - cada aviso usa una clave distinta para no quedar deduplicado")
    void sessionConfirmationReminder_eachReminderHasItsOwnDedupeKey() {
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.of(snapshot));
        LocalDateTime firstReminder = LocalDateTime.of(2026, 10, 20, 15, 0);
        LocalDateTime secondReminder = firstReminder.plusMinutes(10);

        listener.handleSessionConfirmationReminder(new SessionConfirmationReminderEvent(bookingWithId(), firstReminder));
        listener.handleSessionConfirmationReminder(new SessionConfirmationReminderEvent(bookingWithId(), secondReminder));

        verify(notificationService).notifyUser(eq(tutorId), eq(Role.TUTOR),
                eq(NotificationType.SESSION_CONFIRMATION_REMINDER), anyString(), anyString(), eq(bookingId),
                eq("SESSION_CONFIRMATION_REMINDER:" + bookingId + ":" + firstReminder));
        verify(notificationService).notifyUser(eq(tutorId), eq(Role.TUTOR),
                eq(NotificationType.SESSION_CONFIRMATION_REMINDER), anyString(), anyString(), eq(bookingId),
                eq("SESSION_CONFIRMATION_REMINDER:" + bookingId + ":" + secondReminder));
    }

    @Test
    @DisplayName("Token generado - solo el alumno recibe el aviso y el codigo no viaja en el mensaje")
    void sessionConfirmationTokenGenerated_notifiesOnlyTheStudentWithoutTheToken() {
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.of(snapshot));

        listener.handleSessionConfirmationTokenGenerated(
                new SessionConfirmationTokenGeneratedEvent(bookingWithId(), "4821"));

        verify(notificationService).notifyUser(eq(studentId), eq(Role.STUDENT),
                eq(NotificationType.SESSION_CONFIRMATION_TOKEN_AVAILABLE),
                eq("Código de confirmación disponible"),
                eq("Tu clase de Sistemas Operativos con Carlos Lopez empieza a las 15:00."
                        + " Ya podés ver el código en el detalle de la clase para compartirlo con tu tutor."),
                eq(bookingId), eq("SESSION_CONFIRMATION_TOKEN_AVAILABLE:" + bookingId));
        verify(notificationService, never()).notifyUser(eq(tutorId), any(), any(), any(), any(), any(), any());
        verify(notificationService, never()).notifyUser(any(), any(), any(), anyString(), contains("4821"),
                any(), any());
    }

    @Test
    @DisplayName("Sesion no confirmada - ambas partes reciben el mismo aviso con el nombre de la otra parte")
    void sessionNotConfirmed_notifiesBothParties() {
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.of(snapshot));

        listener.handleSessionNotConfirmed(new SessionNotConfirmedEvent(bookingWithId()));

        verify(notificationService).notifyUser(eq(studentId), eq(Role.STUDENT), eq(NotificationType.SESSION_NOT_CONFIRMED),
                eq("Token vencido. Sesión no confirmada"),
                eq("El tiempo para ingresar el código de la sesión con Carlos Lopez venció."
                        + " La reserva pasó al estado \"No confirmada\"."),
                eq(bookingId), eq("SESSION_NOT_CONFIRMED:" + bookingId));
        verify(notificationService).notifyUser(eq(tutorId), eq(Role.TUTOR), eq(NotificationType.SESSION_NOT_CONFIRMED),
                eq("Token vencido. Sesión no confirmada"),
                eq("El tiempo para ingresar el código de la sesión con Ana Estudiante venció."
                        + " La reserva pasó al estado \"No confirmada\"."),
                eq(bookingId), eq("SESSION_NOT_CONFIRMED:" + bookingId));

        verify(notificationService, never()).notifyUser(any(), any(), eq(NotificationType.RATING_ENABLED),
                any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Sesion confirmada - ambas partes reciben SESSION_CONFIRMED y RATING_ENABLED")
    void sessionConfirmed_notifiesBothPartiesAndEnablesRating() {
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.of(snapshot));

        listener.handleSessionConfirmed(new SessionConfirmedEvent(bookingWithId()));

        verify(notificationService).notifyUser(eq(studentId), eq(Role.STUDENT), eq(NotificationType.SESSION_CONFIRMED),
                eq("Sesión confirmada"),
                eq("La sesión de Sistemas Operativos con Carlos Lopez fue confirmada."),
                eq(bookingId), eq("SESSION_CONFIRMED:" + bookingId));
        verify(notificationService).notifyUser(eq(tutorId), eq(Role.TUTOR), eq(NotificationType.SESSION_CONFIRMED),
                eq("Sesión confirmada"),
                eq("La sesión de Sistemas Operativos con Ana Estudiante fue confirmada."),
                eq(bookingId), eq("SESSION_CONFIRMED:" + bookingId));
        verify(notificationService).notifyUser(eq(studentId), eq(Role.STUDENT), eq(NotificationType.RATING_ENABLED),
                eq("Calificá tu sesión de Sistemas Operativos"),
                eq("Contanos cómo fue tu experiencia con Carlos Lopez. Tenés 24 horas para calificar."),
                eq(bookingId), eq("RATING_ENABLED:" + bookingId), eq(RATING_EXPIRES_AT));
        verify(notificationService).notifyUser(eq(tutorId), eq(Role.TUTOR), eq(NotificationType.RATING_ENABLED),
                eq("Calificá tu sesión de Sistemas Operativos"),
                eq("Contanos cómo fue tu experiencia con Ana Estudiante. Tenés 24 horas para calificar."),
                eq(bookingId), eq("RATING_ENABLED:" + bookingId), eq(RATING_EXPIRES_AT));
    }


    @Test
    @DisplayName("Sesion confirmada sin materia - el titulo no imprime null")
    void sessionConfirmed_withoutSubject_usesGenericTitle() {
        BookingSnapshot withoutSubject = new BookingSnapshot(bookingId, studentId, tutorId,
                "Ana Estudiante", "Carlos Lopez", null,
                LocalDate.of(2026, 10, 20), LocalTime.of(15, 0), LocalTime.of(16, 0), null);
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.of(withoutSubject));

        listener.handleSessionConfirmed(new SessionConfirmedEvent(bookingWithId()));

        verify(notificationService).notifyUser(eq(studentId), eq(Role.STUDENT), eq(NotificationType.RATING_ENABLED),
                eq("Calificá tu sesión"), anyString(), eq(bookingId), eq("RATING_ENABLED:" + bookingId),
                any(LocalDateTime.class));
        verify(notificationService).notifyUser(eq(tutorId), eq(Role.TUTOR), eq(NotificationType.RATING_ENABLED),
                eq("Calificá tu sesión"), anyString(), eq(bookingId), eq("RATING_ENABLED:" + bookingId),
                any(LocalDateTime.class));
    }
    @Test
    @DisplayName("Sesion finalizada - ambas partes reciben el aviso de cierre")
    void sessionCompleted_notifiesBothParties() {
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.of(snapshot));

        listener.handleSessionCompleted(new SessionCompletionNotificationEvent(bookingWithId()));

        verify(notificationService).notifyUser(eq(studentId), eq(Role.STUDENT), eq(NotificationType.SESSION_COMPLETED),
                eq("Tu sesión de Sistemas Operativos finalizó"),
                eq("Si hubo algún inconveniente con la asistencia, tenés 24 horas para reclamar."),
                eq(bookingId), eq("SESSION_COMPLETED:" + bookingId));
        verify(notificationService).notifyUser(eq(tutorId), eq(Role.TUTOR), eq(NotificationType.SESSION_COMPLETED),
                eq("Tu sesión de Sistemas Operativos finalizó"),
                eq("Si hubo algún inconveniente con la asistencia, tenés 24 horas para reclamar."),
                eq(bookingId), eq("SESSION_COMPLETED:" + bookingId));
        verify(notificationService, never()).notifyUser(any(), any(), eq(NotificationType.RATING_ENABLED),
                any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Fallo al notificar a una parte - no se propaga y la otra parte igual se notifica")
    void failureOnOneRecipient_doesNotPropagateAndStillNotifiesTheOther() {
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.of(snapshot));
        doThrow(new RuntimeException("database down"))
                .when(notificationService).notifyUser(eq(studentId), eq(Role.STUDENT), eq(NotificationType.BOOKING_CONFIRMED),
                        anyString(), anyString(), eq(bookingId), anyString());

        listener.handleBookingCreated(new BookingCreatedEvent(bookingId));

        verify(notificationService).notifyUser(eq(tutorId), eq(Role.TUTOR), eq(NotificationType.BOOKING_CONFIRMED),
                anyString(), anyString(), eq(bookingId), anyString());
    }

    @Test
    @DisplayName("Reserva inexistente - se loguea el error y no se notifica a nadie")
    void missingBooking_isLoggedAndNoNotificationIsCreated() {
        when(bookingNotificationContext.loadSnapshot(bookingId)).thenReturn(Optional.empty());

        listener.handleBookingCreated(new BookingCreatedEvent(bookingId));

        verify(notificationService, never()).notifyUser(any(), any(), any(), any(), any(), any(), any());
    }

    private Booking bookingWithId() {
        return Booking.builder().bookingId(bookingId).build();
    }
}