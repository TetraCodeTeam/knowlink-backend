package com.knowlink.api.admin.dashboard.controllers.responses;

/**
 * Single payload that feeds every card of the administrator home.
 */
public record DashboardSummaryResponse(
        SessionClaimsBlock sessionClaims,
        MaterialReportsBlock materialReports,
        PerformanceBlock performance) {
}
