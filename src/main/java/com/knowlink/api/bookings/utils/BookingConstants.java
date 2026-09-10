package com.knowlink.api.bookings.utils;

import java.math.BigDecimal;
import java.time.Duration;

public final class BookingConstants {

    private BookingConstants() {
    }

    public static final int HOLD_MINUTES = 15;
    public static final BigDecimal SERVICE_FEE_RATE = BigDecimal.valueOf(0.03);
    public static final int MAX_HISTORY_PAGE_SIZE = 50;
    public static final int CONFIRMATION_TOKEN_DIGITS = 4;
    public static final int CONFIRMATION_TOKEN_MAX_ATTEMPTS = 5;
    public static final Duration CONFIRMATION_TOKEN_LEAD_TIME = Duration.ofMinutes(5);
    public static final Duration CONFIRMATION_WINDOW_GRACE_PERIOD = Duration.ofMinutes(10);
    public static final Duration CONFIRMATION_REMINDER_INTERVAL = Duration.ofMinutes(10);
}