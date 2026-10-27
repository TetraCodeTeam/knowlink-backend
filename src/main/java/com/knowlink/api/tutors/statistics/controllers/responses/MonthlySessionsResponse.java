package com.knowlink.api.tutors.statistics.controllers.responses;

/** {@code month} con formato yyyy-MM. */
public record MonthlySessionsResponse(String month, int completedSessions) {
}
