package com.knowlink.api.shared.utils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Un horario que termina a medianoche (24:00) no se puede guardar como 00:00, porque un LocalTime
 * de 00:00 es el inicio del día y rompería toda comparación "fin > inicio". Se persiste como
 * {@link #TIME}, el mayor valor que entra en una columna TIME(6): conserva el orden en todas las
 * comparaciones y consultas existentes, y solo hay que corregir los cálculos de duración y la
 * conversión a instantes, que pasan por esta clase.
 */
public final class EndOfDay {

    private EndOfDay() {
    }

    public static final LocalTime TIME = LocalTime.of(23, 59, 59, 999_999_000);

    public static final int MINUTES_PER_DAY = 24 * 60;

    public static boolean isEndOfDay(LocalTime time) {
        return time != null && !time.isBefore(TIME);
    }

    /** Un fin a las 00:00 es medianoche del día siguiente: un fin nunca puede ser el inicio del mismo día. */
    public static LocalTime normalizeEnd(LocalTime end) {
        return LocalTime.MIDNIGHT.equals(end) ? TIME : end;
    }

    /** Hora de fin de una ventana expresada como instante: medianoche del día siguiente es fin de día. */
    public static LocalTime endTimeFromInstant(Instant end) {
        return normalizeEnd(end.atZone(AppTimeZone.ZONE).toLocalTime());
    }

    /** Instante del fin de un horario; el fin de día se resuelve a medianoche del día siguiente. */
    public static Instant toInstant(LocalDate date, LocalTime endTime) {
        if (isEndOfDay(endTime)) {
            return date.plusDays(1).atStartOfDay(AppTimeZone.ZONE).toInstant();
        }
        return date.atTime(endTime).atZone(AppTimeZone.ZONE).toInstant();
    }

    public static int minuteOfDay(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    /** Minuto del día en que termina un horario; el fin de día vale 1440. */
    public static int endMinuteOfDay(LocalTime end) {
        return isEndOfDay(end) ? MINUTES_PER_DAY : minuteOfDay(end);
    }

    /** Duración entre un inicio y un fin, contemplando el fin de día. */
    public static Duration between(LocalTime start, LocalTime end) {
        return Duration.ofMinutes(endMinuteOfDay(end) - minuteOfDay(start));
    }

    /** Inverso de {@link #endMinuteOfDay}: 1440 vuelve a ser el fin de día. */
    public static LocalTime endTimeOfMinute(int minuteOfDay) {
        return minuteOfDay >= MINUTES_PER_DAY ? TIME : LocalTime.of(minuteOfDay / 60, minuteOfDay % 60);
    }
}
