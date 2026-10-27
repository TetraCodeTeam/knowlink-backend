package com.knowlink.api.tutors.statistics.controllers.responses;

import java.math.BigDecimal;

/**
 * {@code fulfillmentRate} y {@code averageRating} son null cuando no hay base para calcularlos.
 * {@code netIncome} suma lo liberado y lo pendiente de liberación (sin lo retenido por reclamos).
 */
public record StatisticsSummaryResponse(
        int completedSessions,
        int completedSessionsPreviousPeriod,
        Double fulfillmentRate,
        int eligibleSessions,
        Double averageRating,
        int reviewsCount,
        BigDecimal netIncome,
        BigDecimal commissionPercentage) {
}
