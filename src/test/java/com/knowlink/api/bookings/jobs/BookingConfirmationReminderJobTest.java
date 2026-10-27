package com.knowlink.api.bookings.jobs;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.events.SessionConfirmationReminderEvent;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.shared.utils.AppTimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingConfirmationReminderJobTest {

    @Mock
    private IBookingRepository bookingRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BookingConfirmationReminderJob job;

    @Test
    @DisplayName("Sesion en curso sin recordatorio previo - publica el recordatorio y guarda la marca")
    void dueReminder_publishesEventAndStoresTimestamp() {
        Booking booking = inProgressBooking(null);
        when(bookingRepository.findByBookingStatusInAndConfirmedAtIsNull(anyList()))
                .thenReturn(List.of(booking));

        job.run();

        ArgumentCaptor<SessionConfirmationReminderEvent> captor =
                ArgumentCaptor.forClass(SessionConfirmationReminderEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        verify(bookingRepository).save(booking);
        assertThat(captor.getValue().booking()).isSameAs(booking);
        assertThat(captor.getValue().reminderAt()).isNotNull();
        assertThat(booking.getLastConfirmationReminderAt()).isEqualTo(captor.getValue().reminderAt());
    }

    @Test
    @DisplayName("Ultimo recordatorio hace menos de 10 minutos - no publica otro")
    void reminderNotDue_doesNotPublish() {
        Booking booking = inProgressBooking(LocalDateTime.now(AppTimeZone.ZONE).minusMinutes(5));
        when(bookingRepository.findByBookingStatusInAndConfirmedAtIsNull(anyList()))
                .thenReturn(List.of(booking));

        job.run();

        verifyNoInteractions(eventPublisher);
        verify(bookingRepository, never()).save(booking);
    }

    @Test
    @DisplayName("Clase que todavia no empezo - no publica recordatorio")
    void sessionNotStarted_doesNotPublish() {
        LocalDateTime now = LocalDateTime.now(AppTimeZone.ZONE);
        Booking booking = Booking.builder()
                .bookingId(UUID.randomUUID())
                .bookingStatus(BookingStatus.BOOKED)
                .sessionDate(now.toLocalDate().plusDays(1))
                .startTime(LocalTime.of(15, 0))
                .endTime(LocalTime.of(16, 0))
                .confirmationTokenExpiration(now.plusDays(1).withHour(16).withMinute(10))
                .build();
        when(bookingRepository.findByBookingStatusInAndConfirmedAtIsNull(anyList()))
                .thenReturn(List.of(booking));

        job.run();

        verifyNoInteractions(eventPublisher);
    }

    private Booking inProgressBooking(LocalDateTime lastReminderAt) {
        LocalDateTime now = LocalDateTime.now(AppTimeZone.ZONE);
        return Booking.builder()
                .bookingId(UUID.randomUUID())
                .bookingStatus(BookingStatus.BOOKED)
                .sessionDate(now.toLocalDate())
                .startTime(LocalTime.MIN)
                .endTime(LocalTime.MAX)
                .confirmationTokenExpiration(now.plusHours(1))
                .lastConfirmationReminderAt(lastReminderAt)
                .build();
    }
}