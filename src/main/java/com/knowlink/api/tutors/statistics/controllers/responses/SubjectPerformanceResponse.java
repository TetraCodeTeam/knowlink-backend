package com.knowlink.api.tutors.statistics.controllers.responses;

import com.knowlink.api.tutors.data.enums.CompensationType;

import java.math.BigDecimal;
import java.util.UUID;

/** {@code netIncome} es null en materias gratuitas; {@code deactivated} marca las materias que ya no están activas. */
public record SubjectPerformanceResponse(
        UUID tutorSubjectId,
        String subjectName,
        CompensationType compensationType,
        BigDecimal pricePerHour,
        boolean deactivated,
        int completedSessions,
        Double averageRating,
        int reviewsCount,
        int distinctStudents,
        int recurrentStudents,
        BigDecimal netIncome) {
}
