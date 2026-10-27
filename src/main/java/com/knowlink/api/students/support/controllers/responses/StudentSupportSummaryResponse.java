package com.knowlink.api.students.support.controllers.responses;

public record StudentSupportSummaryResponse(
        SessionClaimsSummary sessionClaims,
        MaterialReportsSummary materialReports) {

    public record SessionClaimsSummary(int total, int pending, int inReview, int resolved) {}

    public record MaterialReportsSummary(int total, int pending, int inReview, int resolved) {}
}
