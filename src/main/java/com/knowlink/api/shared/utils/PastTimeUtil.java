package com.knowlink.api.shared.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public final class PastTimeUtil {

    private static final int ROUNDING_STEP_MINUTES = 30;

    private PastTimeUtil() {
    }

    public static boolean isFullyPast(LocalDate date, LocalTime endTime, LocalDateTime now) {
        return !LocalDateTime.of(date, endTime).isAfter(now);
    }

    // Si el bloque es de hoy y ya empezó, devuelve el próximo horario "en punto
    // de 30 min" desde ahora. En cualquier otro caso, devuelve el startTime tal
    // cual — es puramente para RECORTAR LA VISUALIZACIÓN, nunca modifica lo
    // que el tutor tiene realmente configurado en la base.
    public static LocalTime effectiveStartTime(LocalDate date, LocalTime startTime, LocalDateTime now) {
        if (!date.isEqual(now.toLocalDate()) || !startTime.isBefore(now.toLocalTime())) {
            return startTime;
        }
        int totalMinutes = now.toLocalTime().toSecondOfDay() / 60;
        int roundedUp = ((totalMinutes / ROUNDING_STEP_MINUTES) + 1) * ROUNDING_STEP_MINUTES;
        if (roundedUp >= 24 * 60) return LocalTime.MAX;
        return LocalTime.of(roundedUp / 60, roundedUp % 60);
    }
}