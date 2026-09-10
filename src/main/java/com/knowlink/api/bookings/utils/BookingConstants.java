package com.knowlink.api.bookings.utils;

import java.math.BigDecimal;

public final class BookingConstants {

    private BookingConstants() {
    }

    public static final int HOLD_MINUTES = 15;
    public static final BigDecimal SERVICE_FEE_RATE = BigDecimal.valueOf(0.03);
    public static final int MAX_HISTORY_PAGE_SIZE = 50;
}