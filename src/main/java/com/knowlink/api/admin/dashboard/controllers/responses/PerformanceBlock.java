package com.knowlink.api.admin.dashboard.controllers.responses;

import com.knowlink.api.admin.dashboard.data.enums.DashboardRange;
import com.knowlink.api.admin.dashboard.data.enums.UnresolvedLevel;

/**
 * Performance panel. It changes with the requested range (WEEK or MONTH).
 */
public record PerformanceBlock(
        DashboardRange range,
        Double avgResolutionHours,
        Double avgResolutionDeltaPct,
        double unresolvedPct,
        UnresolvedLevel unresolvedLevel,
        VolumeSplit volumeSplit) {
}
