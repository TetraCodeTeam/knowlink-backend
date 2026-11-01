package com.knowlink.api.admin.dashboard.controllers.responses;

/**
 * Pending counters rendered as badges in the administrator sidebar.
 */
public record AdminNavigationBadgesResponse(
        int pendingMaterialReports,
        int pendingSessionClaims) {
}
