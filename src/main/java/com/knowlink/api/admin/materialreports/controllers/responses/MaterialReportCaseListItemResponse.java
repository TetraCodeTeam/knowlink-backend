package com.knowlink.api.admin.materialreports.controllers.responses;

import com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus;
import com.knowlink.api.materials.data.enums.MaterialType;

import java.time.LocalDateTime;
import java.util.UUID;

public record MaterialReportCaseListItemResponse(
        UUID caseId,
        UUID materialId,
        String fileName,
        MaterialType materialType,
        String subjectName,
        String tutorName,
        int reportsCount,
        MaterialReportCaseStatus status,
        LocalDateTime openedAt,
        AssignedAdminResponse assignedAdmin,
        boolean assignedToMe,
        LocalDateTime resolvedAt,
        String resolutionNote
) {
}