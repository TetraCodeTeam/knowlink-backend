package com.knowlink.api.bookings.jobs;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.events.SessionConfirmationReminderEvent;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.bookings.utils.BookingConstants;
import com.knowlink.api.shared.utils.AppTimeZone;

import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BookingConfirmationReminderJob {

    private final IBookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void run() {
        LocalDateTime now = LocalDateTime.now(AppTimeZone.ZONE);
        List<Booking> candidates = bookingRepository.findByBookingStatusInAndConfirmedAtIsNull(
                List.of(BookingStatus.BOOKED, BookingStatus.IN_PROGRESS));

        for (Booking booking : candidates) {
            if (booking.getConfirmationTokenExpiration() == null) {
                continue; // todavía no se generó el token para esta reserva (Job 1 no la alcanzó)
            }

            LocalDateTime sessionStart = LocalDateTime.of(booking.getSessionDate(), booking.getStartTime());
            if (now.isBefore(sessionStart) || now.isAfter(booking.getConfirmationTokenExpiration()))
                continue;

            boolean dueForReminder = booking.getLastConfirmationReminderAt() == null
                    || Duration.between(booking.getLastConfirmationReminderAt(), now)
                            .compareTo(BookingConstants.CONFIRMATION_REMINDER_INTERVAL) >= 0;

            if (dueForReminder) {
                booking.setLastConfirmationReminderAt(now);
                bookingRepository.save(booking);
                eventPublisher.publishEvent(new SessionConfirmationReminderEvent(booking));
            }
        }
    }
}