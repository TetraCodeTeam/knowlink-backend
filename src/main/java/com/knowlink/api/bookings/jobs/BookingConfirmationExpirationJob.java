package com.knowlink.api.bookings.jobs;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.events.SessionNotConfirmedEvent;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.bookings.utils.BookingConstants;
import com.knowlink.api.shared.utils.AppTimeZone;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BookingConfirmationExpirationJob {

    private final IBookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void run() {
        LocalDateTime now = LocalDateTime.now(AppTimeZone.ZONE);
        List<Booking> expired = bookingRepository
                .findByBookingStatusInAndConfirmedAtIsNullAndConfirmationTokenExpirationBefore(
                        List.of(BookingStatus.BOOKED, BookingStatus.IN_PROGRESS), now);

        for (Booking booking : expired) {
            booking.setBookingStatus(BookingStatus.NOT_CONFIRMED);
            bookingRepository.save(booking);
            eventPublisher.publishEvent(new SessionNotConfirmedEvent(booking));
        }
    }
}
