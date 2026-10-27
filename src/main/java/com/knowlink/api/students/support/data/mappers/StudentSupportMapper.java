package com.knowlink.api.students.support.data.mappers;

import com.knowlink.api.claims.controllers.responses.ClaimAttachmentUrlResponse;
import com.knowlink.api.claims.controllers.responses.ClaimCommentResponse;
import com.knowlink.api.claims.controllers.responses.ClaimFundsSummaryResponse;
import com.knowlink.api.claims.controllers.responses.ClaimReviewProgressResponse;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.claims.data.models.SessionClaim;
import com.knowlink.api.materials.data.enums.MaterialReportReason;
import com.knowlink.api.materials.data.enums.MaterialReportStatus;
import com.knowlink.api.materials.data.models.AcademicMaterialReport;
import com.knowlink.api.students.support.controllers.responses.StudentMaterialReportDetailResponse;
import com.knowlink.api.students.support.controllers.responses.StudentMaterialReportResponse;
import com.knowlink.api.students.support.controllers.responses.StudentSessionClaimDetailResponse;
import com.knowlink.api.students.support.controllers.responses.StudentSessionClaimResponse;
import com.knowlink.api.students.support.controllers.responses.StudentSupportSummaryResponse;
import com.knowlink.api.support.config.SupportProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StudentSupportMapper {

    private final SupportProperties supportProperties;

    public StudentSessionClaimResponse toSessionClaimResponse(SessionClaim claim) {
        var booking = claim.getBooking();
        var tutor = booking.getTutor();
        var tutorProfile = booking.getTutorSubject().getTutorProfile();
        return new StudentSessionClaimResponse(
                claim.getClaimId(),
                booking.getBookingId(),
                tutor.getFullName(),
                tutorProfile.getProfilePictureUrl() == null
                        ? tutor.getProfilePictureUrl()
                        : tutorProfile.getProfilePictureUrl(),
                booking.getTutorSubject().getSubject().getName(),
                booking.getSessionDate(),
                booking.getStartTime(),
                booking.getEndTime(),
                claim.getReason(),
                claimReasonLabel(claim),
                claim.getClaimantRole(),
                claim.getClaimantRole() == Role.STUDENT,
                claim.getStatus(),
                claim.getStatus().displayLabel(),
                claim.getCreatedAt(),
                claim.getUpdatedAt());
    }

    public StudentSessionClaimDetailResponse toSessionClaimDetails(
            SessionClaim claim,
            ClaimFundsSummaryResponse funds,
            ClaimReviewProgressResponse progress,
            List<ClaimAttachmentUrlResponse> attachments,
            List<ClaimCommentResponse> comments) {
        var booking = claim.getBooking();
        var tutor = booking.getTutor();
        var tutorProfile = booking.getTutorSubject().getTutorProfile();
        return new StudentSessionClaimDetailResponse(
                claim.getClaimId(),
                booking.getBookingId(),
                new StudentSessionClaimDetailResponse.TutorSummary(
                        tutor.getFullName(),
                        tutorProfile.getProfilePictureUrl() == null
                                ? tutor.getProfilePictureUrl()
                                : tutorProfile.getProfilePictureUrl()),
                booking.getTutorSubject().getSubject().getName(),
                booking.getTutorSubject().getSubject().getCareer().getName(),
                booking.getSessionDate(),
                booking.getStartTime(),
                booking.getEndTime(),
                claim.getReason(),
                claimReasonLabel(claim),
                claim.getStatus(),
                claim.getStatus().displayLabel(),
                claim.getClaimantRole(),
                claim.getClaimantRole() == Role.STUDENT,
                claim.getClaimant().getFullName(),
                claim.getComment(),
                claim.getCreatedAt(),
                claim.getUpdatedAt(),
                funds,
                progress,
                attachments,
                comments);
    }

    public StudentMaterialReportResponse toMaterialReportResponse(AcademicMaterialReport report) {
        var material = report.getMaterial();
        var tutorProfile = material.getTutorSubject().getTutorProfile();
        MaterialReportStatus status = materialReportStatus(report);
        return new StudentMaterialReportResponse(
                report.getAcademicMaterialReportId(),
                material.getAcademicMaterialId(),
                material.getName(),
                material.getOriginalFileName(),
                material.getMaterialType().name(),
                material.getSizeInBytes(),
                material.getTutorSubject().getSubject().getName(),
                tutorProfile.getUser().getFullName(),
                report.getReason(),
                materialReasonLabel(report.getReason()),
                report.getDescription(),
                status,
                status.displayLabel(),
                report.getResolution(),
                material.isAvailable(),
                report.getReportedAt());
    }

    public StudentMaterialReportDetailResponse toMaterialReportDetails(AcademicMaterialReport report) {
        var material = report.getMaterial();
        var tutorProfile = material.getTutorSubject().getTutorProfile();
        var tutor = tutorProfile.getUser();
        MaterialReportStatus status = materialReportStatus(report);
        return new StudentMaterialReportDetailResponse(
                report.getAcademicMaterialReportId(),
                material.getAcademicMaterialId(),
                material.getName(),
                material.getOriginalFileName(),
                material.getMaterialType().name(),
                material.getSizeInBytes(),
                material.getTutorSubject().getSubject().getName(),
                material.getTutorSubject().getSubject().getCareer().getName(),
                new StudentMaterialReportDetailResponse.TutorSummary(
                        tutor.getUserId(),
                        tutor.getFullName(),
                        tutorProfile.getProfilePictureUrl() == null
                                ? tutor.getProfilePictureUrl()
                                : tutorProfile.getProfilePictureUrl()),
                material.getUploadedAt(),
                material.isAvailable(),
                report.getReason(),
                materialReasonLabel(report.getReason()),
                report.getDescription(),
                status,
                status.displayLabel(),
                report.getResolution(),
                report.getReviewedBy() == null ? null : report.getReviewedBy().getFullName(),
                supportProperties.materialReviewTeamName(),
                report.getReportedAt());
    }

    public StudentSupportSummaryResponse toSummary(
            List<StudentSessionClaimResponse> claims,
            List<StudentMaterialReportResponse> reports) {
        int pendingClaims = (int) claims.stream()
                .filter(claim -> claim.status() == ClaimStatus.OPEN)
                .count();
        int inReviewClaims = (int) claims.stream()
                .filter(claim -> claim.status() == ClaimStatus.IN_REVIEW)
                .count();
        int resolvedClaims = (int) claims.stream()
                .filter(claim -> claim.status() == ClaimStatus.RESOLVED)
                .count();
        int pendingReports = countReports(reports, MaterialReportStatus.PENDING);
        int inReviewReports = countReports(reports, MaterialReportStatus.IN_REVIEW);
        int resolvedReports = countReports(reports, MaterialReportStatus.RESOLVED);
        return new StudentSupportSummaryResponse(
                new StudentSupportSummaryResponse.SessionClaimsSummary(
                        claims.size(), pendingClaims, inReviewClaims, resolvedClaims),
                new StudentSupportSummaryResponse.MaterialReportsSummary(
                        reports.size(), pendingReports, inReviewReports, resolvedReports));
    }

    private int countReports(List<StudentMaterialReportResponse> reports, MaterialReportStatus status) {
        return (int) reports.stream().filter(report -> report.status() == status).count();
    }

    private MaterialReportStatus materialReportStatus(AcademicMaterialReport report) {
        return MaterialReportStatus.orPending(report.getStatus());
    }

    /**
     * La etiqueta se redacta desde la perspectiva del alumno: si el reclamo lo inició el tutor,
     * "I_COULD_NOT_ATTEND" significa que faltó el tutor y "STUDENT_COULD_NOT_ATTEND" que faltó el alumno.
     */
    private String claimReasonLabel(SessionClaim claim) {
        boolean initiatedByStudent = claim.getClaimantRole() == Role.STUDENT;
        return switch (claim.getReason()) {
            case STUDENT_COULD_NOT_ATTEND -> "El alumno no pudo asistir";
            case TUTOR_COULD_NOT_ATTEND -> "El tutor no pudo asistir";
            case I_COULD_NOT_ATTEND -> initiatedByStudent ? "No pude asistir" : "El tutor no pudo asistir";
        };
    }

    private String materialReasonLabel(MaterialReportReason reason) {
        return switch (reason) {
            case INAPPROPRIATE_CONTENT -> "Contenido inapropiado o no pertinente";
            case PLAGIARISM -> "Plagio o derechos de autor";
            case FALSE_INFORMATION -> "Información falsa";
            case OTHER -> "Otro motivo";
        };
    }
}
