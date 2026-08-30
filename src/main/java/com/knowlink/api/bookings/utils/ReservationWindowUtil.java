package com.knowlink.api.bookings.utils;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public final class ReservationWindowUtil {

    private static final Duration WINDOW_DURATION = Duration.ofHours(1);
    private static final Duration STEP = Duration.ofMinutes(30);

    private ReservationWindowUtil() {
    }

    // Mismo criterio que getReservationWindows() del frontend: ventanas de 1h,
    // en pasos de 30 min, dentro del rango del bloque.
    public static List<LocalTime[]> getWindows(LocalTime blockStart, LocalTime blockEnd) {
        List<LocalTime[]> windows = new ArrayList<>();
        LocalTime windowStart = blockStart;

        while (!windowStart.plus(WINDOW_DURATION).isAfter(blockEnd)) {
            windows.add(new LocalTime[]{windowStart, windowStart.plus(WINDOW_DURATION)});
            windowStart = windowStart.plus(STEP);
        }

        return windows;
    }

    public static boolean overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }
}