package com.knowlink.api.bookings.data.mappers;

import com.knowlink.api.bookings.data.enums.BookingHistoryCategory;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BookingHistoryCategoryMapperTest {

    @Test
    @DisplayName("NOT_CONFIRMED es una categoria propia y no se mezcla con CANCELLED")
    void notConfirmedIsItsOwnCategory() {
        assertThat(BookingHistoryCategoryMapper.toStatuses(BookingHistoryCategory.NOT_CONFIRMED))
                .containsExactly(BookingStatus.NOT_CONFIRMED);

        assertThat(BookingHistoryCategoryMapper.toStatuses(BookingHistoryCategory.CANCELLED))
                .containsExactly(
                        BookingStatus.CANCELLED,
                        BookingStatus.NOT_FULFILLED_BY_TUTOR,
                        BookingStatus.SESSION_NOT_HELD,
                        BookingStatus.EXPIRED)
                .doesNotContain(BookingStatus.NOT_CONFIRMED);
    }

    @Test
    @DisplayName("RESERVED mantiene PENDING y BOOKED")
    void reservedKeepsPendingAndBooked() {
        assertThat(BookingHistoryCategoryMapper.toStatuses(BookingHistoryCategory.RESERVED))
                .containsExactly(BookingStatus.PENDING, BookingStatus.BOOKED);
    }
}
