package com.knowlink.api.bookings.data.mappers;

import com.knowlink.api.bookings.data.enums.BookingHistoryCategory;
import com.knowlink.api.bookings.data.enums.BookingStatus;

import java.util.List;

public final class BookingHistoryCategoryMapper {

    private BookingHistoryCategoryMapper() {
    }

    public static List<BookingStatus> toStatuses(BookingHistoryCategory category) {
        return switch (category) {
            case RESERVED -> List.of(BookingStatus.PENDING, BookingStatus.BOOKED);
            case IN_PROGRESS -> List.of(BookingStatus.IN_PROGRESS);
            case COMPLETED -> List.of(BookingStatus.COMPLETED);
            case NOT_CONFIRMED -> List.of(BookingStatus.NOT_CONFIRMED);
            case CANCELLED -> List.of(
                    BookingStatus.CANCELLED,
                    BookingStatus.NOT_FULFILLED_BY_TUTOR,
                    BookingStatus.SESSION_NOT_HELD,
                    BookingStatus.EXPIRED);
        };
    }
}
