package com.knowlink.api.tutors.support.controllers.responses;

import com.knowlink.api.materials.data.enums.MaterialReportReason;
import com.knowlink.api.materials.data.enums.MaterialReportResolution;
import com.knowlink.api.materials.data.enums.MaterialReportStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TutorReportedMaterialDetailResponse(
        UUID materialId,
        String name,
        String originalFileName,
        String format,
        Long sizeInBytes,
        String subjectName,
        String careerName,
        LocalDateTime uploadedAt,
        boolean available,
        String previewUrl,
        int reportCount,
        MaterialReportStatus status,
        String reviewTeamName,
        String reviewerName,
        List<ReportDetail> reports,
        DefenseDetail defense) {

    public record ReportDetail(
            UUID reportId,
            MaterialReportReason reason,
            String description,
            LocalDateTime reportedAt,
            MaterialReportStatus status,
            MaterialReportResolution resolution) {}

    public record DefenseDetail(
            boolean submitted,
            String description,
            LocalDateTime submittedAt,
            List<DefenseAttachment> attachments) {}

    public record DefenseAttachment(
            UUID attachmentId,
            String fileName,
            String contentType,
            long sizeInBytes,
            String signedUrl) {}
}
