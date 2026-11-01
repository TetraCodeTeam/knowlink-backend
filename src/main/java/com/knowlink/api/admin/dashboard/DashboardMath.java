package com.knowlink.api.admin.dashboard;

import com.knowlink.api.admin.dashboard.controllers.responses.VolumeSplit;
import com.knowlink.api.admin.dashboard.data.enums.UnresolvedLevel;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure calculations of the admin dashboard. Kept free of Spring and persistence so every formula
 * can be tested in isolation.
 */
public final class DashboardMath {

    private DashboardMath() {
    }

    /**
     * Percentage variation between two counts. Returns null when there is no comparison base.
     */
    public static Double percentageDelta(long current, long previous) {
        if (previous == 0) {
            return null;
        }
        return round1((current - previous) * 100.0 / previous);
    }

    /**
     * Variation of the average resolution time. Positive means the current period was faster.
     */
    public static Double ttrDeltaPct(Double previousAvgHours, Double currentAvgHours) {
        if (previousAvgHours == null || currentAvgHours == null || previousAvgHours == 0.0) {
            return null;
        }
        return round1((previousAvgHours - currentAvgHours) / previousAvgHours * 100.0);
    }

    public static double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    public static UnresolvedLevel unresolvedLevel(double unresolvedPct) {
        if (unresolvedPct < 15.0) {
            return UnresolvedLevel.UNDER_CONTROL;
        }
        if (unresolvedPct < 30.0) {
            return UnresolvedLevel.ACCEPTABLE;
        }
        if (unresolvedPct < 60.0) {
            return UnresolvedLevel.RISKY;
        }
        return UnresolvedLevel.CRITICAL;
    }

    /**
     * Splits the volume by largest remainder so both integer percentages add up to 100.
     */
    public static VolumeSplit volumeSplit(long claims, long reports) {
        long total = claims + reports;
        if (total == 0) {
            return new VolumeSplit(0, 0);
        }

        double claimsExact = claims * 100.0 / total;
        double reportsExact = reports * 100.0 / total;
        int claimsPct = (int) Math.floor(claimsExact);
        int reportsPct = (int) Math.floor(reportsExact);
        int remaining = 100 - claimsPct - reportsPct;

        if (remaining > 0) {
            double claimsRemainder = claimsExact - claimsPct;
            double reportsRemainder = reportsExact - reportsPct;
            // Ties go to claims so the result is deterministic.
            if (claimsRemainder >= reportsRemainder) {
                claimsPct += remaining;
            } else {
                reportsPct += remaining;
            }
        }

        return new VolumeSplit(claimsPct, reportsPct);
    }
}
