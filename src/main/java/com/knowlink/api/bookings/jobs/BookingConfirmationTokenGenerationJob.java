package com.knowlink.api.bookings.jobs;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.events.SessionConfirmationTokenGeneratedEvent;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.bookings.services.interfaces.IBookingConfirmationTokenService;
import com.knowlink.api.bookings.utils.BookingConstants;
import com.knowlink.api.shared.utils.AppTimeZone;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingConfirmationTokenGenerationJob {

    private final IBookingRepository bookingRepository;
    private final IBookingConfirmationTokenService tokenService;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void run() {
        LocalDateTime now = LocalDateTime.now(AppTimeZone.ZONE);
        List<Booking> candidates = bookingRepository
                .findByBookingStatusAndConfirmationTokenIsNull(BookingStatus.BOOKED);

        for (Booking booking : candidates) {
            LocalDateTime sessionStart = LocalDateTime.of(booking.getSessionDate(), booking.getStartTime());

            if (!sessionStart.isAfter(now.plus(BookingConstants.CONFIRMATION_TOKEN_LEAD_TIME))
                    && now.isBefore(LocalDateTime.of(booking.getSessionDate(), booking.getEndTime())
                            .plus(BookingConstants.CONFIRMATION_WINDOW_GRACE_PERIOD))) {
                String rawToken = tokenService.generateToken();

                // El token se entrega únicamente mediante
                // SessionConfirmationTokenGeneratedEvent.
                booking.setConfirmationToken(tokenService.encrypt(rawToken));

                LocalDateTime sessionEnd = LocalDateTime.of(booking.getSessionDate(), booking.getEndTime());

                booking.setConfirmationToken(tokenService.encrypt(rawToken));
                booking.setConfirmationTokenExpiration(
                        sessionEnd.plus(BookingConstants.CONFIRMATION_WINDOW_GRACE_PERIOD));
                bookingRepository.save(booking);
                eventPublisher.publishEvent(new SessionConfirmationTokenGeneratedEvent(booking, rawToken));
            }
        }
    }
}