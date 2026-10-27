package com.knowlink.api.tutors.statistics.controllers.responses;

import com.knowlink.api.tutors.statistics.data.enums.AvailabilityBand;

import java.time.DayOfWeek;

/** {@code percentage} es null cuando la franja no tuvo horas ofrecidas (distinto de 0% de ocupación). */
public record OccupancyCellResponse(
        DayOfWeek dayOfWeek,
        AvailabilityBand band,
        double bookedHours,
        double offeredHours,
        Integer percentage) {
}
