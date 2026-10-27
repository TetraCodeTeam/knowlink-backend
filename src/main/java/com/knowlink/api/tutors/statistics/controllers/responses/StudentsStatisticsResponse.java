package com.knowlink.api.tutors.statistics.controllers.responses;

/** Un alumno es recurrente con 2 o más sesiones completadas con el tutor en el período. */
public record StudentsStatisticsResponse(
        int distinct,
        int recurrent,
        int recurrentPercentage,
        int sessionsWithRecurrentStudents,
        int sessionsWithNewStudents) {
}
