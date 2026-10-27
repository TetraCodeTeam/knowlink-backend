package com.knowlink.api.students.support.controllers.responses;

import com.knowlink.api.materials.data.enums.MaterialReportReason;
import com.knowlink.api.materials.data.enums.MaterialReportResolution;
import com.knowlink.api.materials.data.enums.MaterialReportStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record StudentMaterialReportResponse(
        UUID reportId,
        UUID materialId,
        String name,
        String originalFileName,
        String format,
        Long sizeInBytes,
        String subjectName,
        String tutorName,
        MaterialReportReason reason,
        String reasonLabel,
        String description,
        MaterialReportStatus status,
        String statusLabel,
        MaterialReportResolution resolution,
        boolean materialAvailable,
        LocalDateTime reportedAt) {}
