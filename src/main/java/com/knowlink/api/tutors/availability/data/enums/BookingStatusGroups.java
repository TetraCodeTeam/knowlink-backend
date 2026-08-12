package com.knowlink.api.tutors.availability.data.enums;

import com.knowlink.api.tutors.data.enums.BookingStatus;

import java.util.Arrays;
import java.util.List;

public final class BookingStatusGroups {

    private BookingStatusGroups() {
    }

    // Estados donde la reserva depende de que su TimeSlot/AvailabilityBlock de
    // origen sigan vigentes - hay un alumno con una sesión real o a punto de
    // confirmarse.
    public static final List<BookingStatus> ACTIVE = List.of(
            BookingStatus.PENDING, BookingStatus.BOOKED, BookingStatus.IN_PROGRESS);

    public static final List<BookingStatus> INACTIVE = Arrays.stream(BookingStatus.values())
            .filter(status -> !ACTIVE.contains(status))
            .toList();
}
