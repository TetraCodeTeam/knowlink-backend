package com.knowlink.api.tutors.support.data.mappers;

import com.knowlink.api.claims.controllers.responses.ClaimAttachmentUrlResponse;
import com.knowlink.api.claims.controllers.responses.ClaimCommentResponse;
import com.knowlink.api.claims.controllers.responses.ClaimFundsSummaryResponse;
import com.knowlink.api.claims.controllers.responses.ClaimReviewProgressResponse;
import com.knowlink.api.claims.data.models.SessionClaim;
import com.knowlink.api.materials.data.enums.MaterialReportStatus;
import com.knowlink.api.materials.data.models.AcademicMaterial;
import com.knowlink.api.materials.data.models.AcademicMaterialDefense;
import com.knowlink.api.materials.data.models.AcademicMaterialReport;
import com.knowlink.api.materials.data.models.MaterialDefenseAttachment;
import com.knowlink.api.support.config.SupportProperties;
import com.knowlink.api.tutors.support.controllers.responses.TutorReportedMaterialDetailResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorReportedMaterialResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorSupportClaimDetailResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorSupportClaimResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorSupportSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TutorSupportMapper {

    private final SupportProperties supportProperties;

    public TutorSupportClaimResponse toClaimResponse(SessionClaim claim) {
        var booking = claim.getBooking();
        return new TutorSupportClaimResponse(
                claim.getClaimId(),
                booking.getBookingId(),
                booking.getTutorSubject().getSubject().getName(),
                booking.getStudent().getFullName(),
                booking.getSessionDate(),
                booking.getStartTime(),
                booking.getEndTime(),
                claim.getReason(),
                claim.getStatus(),
                claim.getCreatedAt(),
                claim.getUpdatedAt());
    }

    public TutorSupportClaimDetailResponse toClaimDetails(
            SessionClaim claim,
            String studentPictureUrl,
            String studentCareer,
            ClaimFundsSummaryResponse funds,
            ClaimReviewProgressResponse progress,
            List<ClaimAttachmentUrlResponse> attachments,
            List<ClaimCommentResponse> comments) {
        var booking = claim.getBooking();
        return new TutorSupportClaimDetailResponse(
                claim.getClaimId(),
                booking.getBookingId(),
                new TutorSupportClaimDetailResponse.StudentSummary(
                        booking.getStudent().getFullName(), studentPictureUrl, studentCareer),
                booking.getTutorSubject().getSubject().getName(),
                booking.getTutorSubject().getSubject().getCareer().getName(),
                booking.getSessionDate(),
                booking.getStartTime(),
                booking.getEndTime(),
                claim.getReason(),
                claim.getStatus(),
                claim.getClaimantRole(),
                claim.getClaimant().getFullName(),
                claim.getComment(),
                claim.getCreatedAt(),
                claim.getUpdatedAt(),
                funds,
                progress,
                attachments,
                comments);
    }

    public List<TutorReportedMaterialResponse> toReportedMaterials(List<AcademicMaterialReport> reports) {
        Map<UUID, List<AcademicMaterialReport>> reportsByMaterial = new LinkedHashMap<>();
        for (AcademicMaterialReport report : reports) {
            UUID materialId = report.getMaterial().getAcademicMaterialId();
            reportsByMaterial.computeIfAbsent(materialId, ignored -> new ArrayList<>()).add(report);
        }

        return reportsByMaterial.values().stream()
                .map(this::toReportedMaterialResponse)
                .toList();
    }

    public TutorReportedMaterialDetailResponse toReportedMaterialDetails(
            AcademicMaterial material,
            List<AcademicMaterialReport> reports,
            AcademicMaterialDefense defense,
            String previewUrl,
            List<TutorReportedMaterialDetailResponse.DefenseAttachment> defenseAttachments) {
        String reviewerName = reports.stream()
                .map(AcademicMaterialReport::getReviewedBy)
                .filter(Objects::nonNull)
                .map(user -> user.getFullName())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        return new TutorReportedMaterialDetailResponse(
                material.getAcademicMaterialId(),
                material.getName(),
                material.getOriginalFileName(),
                material.getMaterialType().name(),
                material.getSizeInBytes(),
                material.getTutorSubject().getSubject().getName(),
                material.getTutorSubject().getSubject().getCareer().getName(),
                material.getUploadedAt(),
                material.isAvailable(),
                previewUrl,
                reports.size(),
                MaterialReportStatus.aggregate(reports.stream()
                        .map(AcademicMaterialReport::getStatus)
                        .toList()),
                supportProperties.materialReviewTeamName(),
                reviewerName,
                reports.stream().map(this::toReportDetail).toList(),
                new TutorReportedMaterialDetailResponse.DefenseDetail(
                        defense != null,
                        defense == null ? null : defense.getDescription(),
                        defense == null ? null : defense.getSubmittedAt(),
                        defenseAttachments));
    }

    public TutorReportedMaterialDetailResponse.DefenseAttachment toDefenseAttachment(
            MaterialDefenseAttachment attachment,
            String signedUrl) {
        return new TutorReportedMaterialDetailResponse.DefenseAttachment(
                attachment.getAttachmentId(),
                attachment.getOriginalFileName(),
                attachment.getContentType(),
                attachment.getSizeInBytes(),
                signedUrl);
    }

    public TutorSupportSummaryResponse toSummary(
            List<TutorSupportClaimResponse> claims,
            List<TutorReportedMaterialResponse> reportedMaterials) {
        return new TutorSupportSummaryResponse(
                claims.size(),
                reportedMaterials.size(),
                (int) reportedMaterials.stream()
                        .filter(material -> material.status() == MaterialReportStatus.IN_REVIEW)
                        .count());
    }

    private TutorReportedMaterialResponse toReportedMaterialResponse(List<AcademicMaterialReport> reports) {
        AcademicMaterialReport mostRecentReport = reports.get(0);
        AcademicMaterial material = mostRecentReport.getMaterial();
        return new TutorReportedMaterialResponse(
                material.getAcademicMaterialId(),
                material.getName(),
                material.getOriginalFileName(),
                material.getMaterialType().name(),
                material.getSizeInBytes(),
                material.getTutorSubject().getSubject().getName(),
                material.getUploadedAt(),
                material.isAvailable(),
                reports.size(),
                MaterialReportStatus.aggregate(reports.stream()
                        .map(AcademicMaterialReport::getStatus)
                        .toList()),
                mostRecentReport.getReportedAt());
    }

    private TutorReportedMaterialDetailResponse.ReportDetail toReportDetail(AcademicMaterialReport report) {
        return new TutorReportedMaterialDetailResponse.ReportDetail(
                report.getAcademicMaterialReportId(),
                report.getReason(),
                report.getDescription(),
                report.getReportedAt(),
                MaterialReportStatus.orPending(report.getStatus()),
                report.getResolution());
    }
}
