package com.knowlink.api.tutors.statistics.controllers.responses;

import java.util.List;

/** {@code cells} trae las 21 combinaciones día × franja; {@code percentage} es null sin horas ofrecidas. */
public record OccupancyResponse(
        double bookedHours,
        double offeredHours,
        Integer percentage,
        List<OccupancyCellResponse> cells) {
}
