package com.knowlink.api.bookings.utils;

import com.knowlink.api.shared.utils.EndOfDay;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public final class ReservationWindowUtil {

    private static final int WINDOW_MINUTES = (int) Duration.ofHours(1).toMinutes();
    private static final int STEP_MINUTES = (int) Duration.ofMinutes(30).toMinutes();

    private ReservationWindowUtil() {
    }

    // Mismo criterio que getReservationWindows() del frontend: ventanas de 1h,
    // en pasos de 30 min, dentro del rango del bloque. Se calcula en minutos del
    // día (y no con LocalTime.plus) para que un bloque que termina a medianoche
    // no genere ventanas que dan la vuelta al día.
    public static List<LocalTime[]> getWindows(LocalTime blockStart, LocalTime blockEnd) {
        if (blockStart == null || blockEnd == null || !blockEnd.isAfter(blockStart)) {
            return List.of();
        }
        int blockEndMinute = EndOfDay.endMinuteOfDay(blockEnd);
        List<LocalTime[]> windows = new ArrayList<>();

        for (int start = EndOfDay.minuteOfDay(blockStart); start + WINDOW_MINUTES <= blockEndMinute;
                start += STEP_MINUTES) {
            windows.add(new LocalTime[] {
                    LocalTime.of(start / 60, start % 60),
                    EndOfDay.endTimeOfMinute(start + WINDOW_MINUTES) });
        }

        return windows;
    }

    public static boolean overlaps(LocalTime aStart, LocalTime aEnd, LocalTime bStart, LocalTime bEnd) {
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }
}
