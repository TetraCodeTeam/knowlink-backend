package com.knowlink.api.tutors.availability.data.enums;

import com.knowlink.api.bookings.data.enums.BookingStatus;

import java.util.Arrays;
import java.util.List;

public final class BookingStatusGroups {

    private BookingStatusGroups() {
    }

    // Statuses whose booking still occupies its schedule window: the session is
    // planned, booked, running, or attendance was already confirmed. COMPLETED
    // means "attendance confirmed", not "the time window ended" - a class
    // confirmed while it was still running keeps occupying its slot until the
    // window is over, so every overlap check must include it.
    public static final List<BookingStatus> OCCUPYING = List.of(
            BookingStatus.PENDING, BookingStatus.BOOKED, BookingStatus.IN_PROGRESS,
            BookingStatus.COMPLETED);

    // Statuses where the booking no longer occupies any schedule window
    // (expired, cancelled, or the session was never held).
    public static final List<BookingStatus> INACTIVE = Arrays.stream(BookingStatus.values())
            .filter(status -> !OCCUPYING.contains(status))
            .toList();
}
