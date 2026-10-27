package com.knowlink.api.tutors.statistics.controllers.responses;

import java.util.List;

/**
 * Panel completo en una sola respuesta, para que todas las métricas correspondan al mismo período
 * y al mismo instante. {@code funds} es null cuando el tutor solo ofrece materias gratuitas.
 */
public record TutorStatisticsResponse(
        StatisticsPeriodResponse period,
        boolean hasActivity,
        StatisticsSummaryResponse summary,
        List<MonthlySessionsResponse> monthlySessions,
        UnheldSessionsResponse unheldSessions,
        FundsStatusResponse funds,
        RatingDistributionResponse ratings,
        StudentsStatisticsResponse students,
        OccupancyResponse occupancy,
        List<SubjectPerformanceResponse> subjects) {
}
