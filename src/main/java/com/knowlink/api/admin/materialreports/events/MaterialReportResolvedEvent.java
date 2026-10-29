package com.knowlink.api.admin.materialreports.events;

import com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus;

import java.util.Set;
import java.util.UUID;

public record MaterialReportResolvedEvent(
        UUID caseId,
        MaterialReportCaseStatus resolution,
        UUID tutorUserId,
        String materialName,
        String removalReason,
        Set<UUID> reporterUserIds
) {
}