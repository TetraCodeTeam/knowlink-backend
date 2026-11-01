package com.knowlink.api.admin.dashboard.controllers.responses;

/**
 * Share of the new cases of the period by type. Both percentages add up to 100 when there is volume.
 */
public record VolumeSplit(int claimsPct, int reportsPct) {
}
