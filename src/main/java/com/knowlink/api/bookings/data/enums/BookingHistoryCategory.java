package com.knowlink.api.bookings.data.enums;

public enum BookingHistoryCategory {
    RESERVED,
    IN_PROGRESS,
    COMPLETED,
    NOT_CONFIRMED,
    CANCELLED;

    public boolean isUpcoming() {
        return this == RESERVED || this == IN_PROGRESS;
    }
}