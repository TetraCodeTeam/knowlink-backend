package com.knowlink.api.bookings.data.mappers;

import com.knowlink.api.bookings.data.enums.BookingHistoryCategory;
import com.knowlink.api.bookings.data.enums.BookingStatus;

import java.util.List;

public final class BookingHistoryCategoryMapper {

    private BookingHistoryCategoryMapper() {
    }

    public static List<BookingStatus> toStatuses(BookingHistoryCategory category) {
        return switch (category) {
            case RESERVED -> List.of(BookingStatus.BOOKED);
            case IN_PROGRESS -> List.of(BookingStatus.IN_PROGRESS);
            case COMPLETED -> List.of(BookingStatus.COMPLETED);
            // PENDING y EXPIRED quedan afuera a propósito: son solicitudes que
            // nunca llegaron a ser reserva confirmada, no una "clase" en Mis Clases.
            case CANCELLED -> List.of(BookingStatus.CANCELLED, BookingStatus.NOT_CONFIRMED);
        };
    }
}