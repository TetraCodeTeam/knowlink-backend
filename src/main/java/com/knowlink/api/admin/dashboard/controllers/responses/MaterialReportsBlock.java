package com.knowlink.api.admin.dashboard.controllers.responses;

/**
 * Weekly card with the material reports metrics. It does not depend on the selected range.
 */
public record MaterialReportsBlock(
        int resolvedThisWeek,
        Double resolvedDeltaPct,
        int newThisWeek,
        int pendingCount,
        MostReportedFile mostReportedPendingFile) {
}
