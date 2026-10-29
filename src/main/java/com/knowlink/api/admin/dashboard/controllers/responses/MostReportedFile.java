package com.knowlink.api.admin.dashboard.controllers.responses;

import java.util.UUID;

/**
 * Pending material case with the highest number of reports.
 */
public record MostReportedFile(
        UUID caseId,
        UUID materialId,
        String fileName,
        int reportsCount) {
}
