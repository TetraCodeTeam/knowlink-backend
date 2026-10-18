package com.knowlink.api.tutors.support.controllers.responses;

import com.knowlink.api.materials.data.enums.MaterialReportStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record TutorReportedMaterialResponse(
        UUID materialId,
        String name,
        String originalFileName,
        String format,
        Long sizeInBytes,
        String subjectName,
        LocalDateTime uploadedAt,
        boolean available,
        int reportCount,
        MaterialReportStatus status,
        LocalDateTime lastReportedAt) {
}
