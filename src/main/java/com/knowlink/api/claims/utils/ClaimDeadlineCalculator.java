package com.knowlink.api.claims.utils;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.models.Booking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class ClaimDeadlineCalculator {

    private final Clock clock;
    private final long deadlineHours;

    public ClaimDeadlineCalculator(Clock clock,
            @Value("${knowlink.claims.deadline-hours:24}") long deadlineHours) {
        this.clock = clock;
        this.deadlineHours = deadlineHours;
    }

    public boolean isFinalized(Booking booking) {
        return booking.getBookingStatus() == BookingStatus.COMPLETED;
    }

    public LocalDateTime deadline(Booking booking) {
        return LocalDateTime.of(booking.getSessionDate(), booking.getEndTime()).plusHours(deadlineHours);
    }

    public boolean isWithinDeadline(Booking booking) {
        return !LocalDateTime.now(clock).isAfter(deadline(booking));
    }
}