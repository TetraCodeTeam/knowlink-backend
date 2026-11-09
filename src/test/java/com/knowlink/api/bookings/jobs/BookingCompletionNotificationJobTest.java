package com.knowlink.api.bookings.jobs;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.events.SessionCompletionNotificationEvent;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.claims.utils.ClaimDeadlineCalculator;
import com.knowlink.api.shared.utils.AppTimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingCompletionNotificationJobTest {

    private static final List<BookingStatus> CANDIDATE_STATUSES =
            List.of(BookingStatus.BOOKED, BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED,
                    BookingStatus.NOT_CONFIRMED);

    @Mock
    private IBookingRepository bookingRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private BookingCompletionNotificationJob job;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                LocalDateTime.of(2026, 10, 20, 12, 0).atZone(AppTimeZone.ZONE).toInstant(),
                AppTimeZone.ZONE);
        job = new BookingCompletionNotificationJob(
                bookingRepository,
                eventPublisher,
                new ClaimDeadlineCalculator(clock, 24));
    }

    @Test
    @DisplayName("run - no publica el evento si la sesión ya fue confirmada con token")
    void run_confirmedSession_marksProcessedButDoesNotPublish() {
        Booking booking = endedBooking(BookingStatus.BOOKED, LocalDateTime.now(AppTimeZone.ZONE));
        when(bookingRepository.findByCompletionNotificationSentFalseAndBookingStatusIn(CANDIDATE_STATUSES))
                .thenReturn(List.of(booking));

        job.run();

        assertThat(booking.isCompletionNotificationSent()).isTrue();
        verify(bookingRepository).save(booking);
        verify(eventPublisher, never()).publishEvent(any(SessionCompletionNotificationEvent.class));
    }

    @Test
    @DisplayName("run - no publica el evento en estados no reclamables")
    void run_notClaimableStatus_doesNotPublish() {
        Booking booking = endedBooking(BookingStatus.COMPLETED, null);
        when(bookingRepository.findByCompletionNotificationSentFalseAndBookingStatusIn(CANDIDATE_STATUSES))
                .thenReturn(List.of(booking));

        job.run();

        assertThat(booking.isCompletionNotificationSent()).isTrue();
        verify(bookingRepository).save(booking);
        verify(eventPublisher, never()).publishEvent(any(SessionCompletionNotificationEvent.class));
    }

    @Test
    @DisplayName("run - publica el evento cuando la sesión sigue siendo reclamable")
    void run_notConfirmedSession_publishesEvent() {
        Booking booking = endedBooking(BookingStatus.NOT_CONFIRMED, null);
        when(bookingRepository.findByCompletionNotificationSentFalseAndBookingStatusIn(CANDIDATE_STATUSES))
                .thenReturn(List.of(booking));

        job.run();

        assertThat(booking.isCompletionNotificationSent()).isTrue();
        verify(bookingRepository).save(booking);
        verify(eventPublisher).publishEvent(any(SessionCompletionNotificationEvent.class));
    }

    @Test
    @DisplayName("run - no procesa una sesión que todavía no terminó")
    void run_sessionStillRunning_doesNotProcess() {
        // "now + 1h" crosses midnight between 23:00 and 00:00, which would move the session end
        // to the past and make the job process it as if the session had already finished.
        LocalTime now = LocalTime.now(AppTimeZone.ZONE);
        LocalDate sessionDate = LocalDate.now(AppTimeZone.ZONE);
        LocalTime endTime = now.plusHours(1);
        if (endTime.isBefore(now)) {
            sessionDate = sessionDate.plusDays(1);
        }
        Booking booking = Booking.builder()
                .bookingId(UUID.randomUUID())
                .sessionDate(sessionDate)
                .startTime(now.plusMinutes(1))
                .endTime(endTime)
                .bookingStatus(BookingStatus.IN_PROGRESS)
                .build();
        when(bookingRepository.findByCompletionNotificationSentFalseAndBookingStatusIn(CANDIDATE_STATUSES))
                .thenReturn(List.of(booking));

        job.run();

        assertThat(booking.isCompletionNotificationSent()).isFalse();
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(eventPublisher, never()).publishEvent(any(SessionCompletionNotificationEvent.class));
    }

    private Booking endedBooking(BookingStatus status, LocalDateTime confirmedAt) {
        return Booking.builder()
                .bookingId(UUID.randomUUID())
                .sessionDate(LocalDate.now(AppTimeZone.ZONE).minusDays(1))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .bookingStatus(status)
                .confirmedAt(confirmedAt)
                .build();
    }
}