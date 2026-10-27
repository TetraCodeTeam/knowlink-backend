package com.knowlink.api.students.support.controllers.responses;

import com.knowlink.api.materials.data.enums.MaterialReportReason;
import com.knowlink.api.materials.data.enums.MaterialReportResolution;
import com.knowlink.api.materials.data.enums.MaterialReportStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record StudentMaterialReportDetailResponse(
        UUID reportId,
        UUID materialId,
        String name,
        String originalFileName,
        String format,
        Long sizeInBytes,
        String subjectName,
        String careerName,
        TutorSummary tutor,
        LocalDateTime materialUploadedAt,
        boolean materialAvailable,
        MaterialReportReason reason,
        String reasonLabel,
        String description,
        MaterialReportStatus status,
        String statusLabel,
        MaterialReportResolution resolution,
        String reviewerName,
        String reviewTeamName,
        LocalDateTime reportedAt) {

    public record TutorSummary(UUID userId, String fullName, String profilePictureUrl) {}
}
