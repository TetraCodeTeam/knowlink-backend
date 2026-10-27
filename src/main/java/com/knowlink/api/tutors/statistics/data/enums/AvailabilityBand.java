package com.knowlink.api.tutors.statistics.data.enums;

import com.knowlink.api.shared.utils.EndOfDay;

import java.time.LocalTime;

/**
 * Franjas horarias del panel de ocupación. Los límites cubren las 24 h para que ningún minuto
 * ofrecido quede fuera de una franja, aunque la agenda hoy solo permita horarios diurnos.
 */
public enum AvailabilityBand {
    MORNING(0, 12 * 60),
    AFTERNOON(12 * 60, 18 * 60),
    NIGHT(18 * 60, 24 * 60);

    private final int startMinute;
    private final int endMinute;

    AvailabilityBand(int startMinute, int endMinute) {
        this.startMinute = startMinute;
        this.endMinute = endMinute;
    }

    /** Minutos del intervalo [start, end) (en minutos del día) que caen dentro de esta franja. */
    public int overlapMinutes(int start, int end) {
        return Math.max(0, Math.min(end, endMinute) - Math.max(start, startMinute));
    }

    public static int minuteOfDay(LocalTime time) {
        return EndOfDay.minuteOfDay(time);
    }

    /** El fin de día (24:00) se guarda como EndOfDay.TIME y vale 1440 minutos. */
    public static int endMinuteOfDay(LocalTime end) {
        return EndOfDay.endMinuteOfDay(end);
    }
}
