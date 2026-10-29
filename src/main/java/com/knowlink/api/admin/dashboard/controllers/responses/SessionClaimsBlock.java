package com.knowlink.api.admin.dashboard.controllers.responses;

/**
 * Weekly card with the session claims metrics. It does not depend on the selected range.
 */
public record SessionClaimsBlock(
        int resolvedThisWeek,
        Double resolvedDeltaPct,
        int newThisWeek,
        int pendingCount,
        Integer oldestPendingDays) {
}
