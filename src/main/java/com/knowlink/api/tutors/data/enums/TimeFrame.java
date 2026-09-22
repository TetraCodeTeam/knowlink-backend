package com.knowlink.api.tutors.data.enums;

import java.time.LocalTime;

/**
 * Franjas horarias fijas para el filtro de disponibilidad horaria (US-49).
 * Un bloque de disponibilidad se considera dentro de una franja si se solapa
 * con ella: blockStart < frameEnd && blockEnd > frameStart.
 */
public enum TimeFrame {
    MORNING(LocalTime.of(6, 0), LocalTime.of(12, 0)),
    AFTERNOON(LocalTime.of(12, 0), LocalTime.of(18, 0)),
    EVENING(LocalTime.of(18, 0), LocalTime.of(23, 59));

    private final LocalTime start;
    private final LocalTime end;

    TimeFrame(LocalTime start, LocalTime end) {
        this.start = start;
        this.end = end;
    }

    public LocalTime getStart() {
        return start;
    }

    public LocalTime getEnd() {
        return end;
    }
}