package com.knowlink.api.admin.materialreports.data.mappers;

import com.knowlink.api.admin.materialreports.controllers.responses.AssignedAdminResponse;
import com.knowlink.api.admin.materialreports.controllers.responses.MaterialInfoResponse;
import com.knowlink.api.admin.materialreports.controllers.responses.MaterialReportCaseDetailResponse;
import com.knowlink.api.admin.materialreports.controllers.responses.MaterialReportCaseListItemResponse;
import com.knowlink.api.admin.materialreports.controllers.responses.MaterialReportItemResponse;
import com.knowlink.api.admin.materialreports.controllers.responses.PermissionsResponse;
import com.knowlink.api.admin.materialreports.controllers.responses.TutorOwnerResponse;
import com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus;
import com.knowlink.api.admin.materialreports.data.models.MaterialReportCase;
import com.knowlink.api.materials.data.models.AcademicMaterial;
import com.knowlink.api.materials.data.models.AcademicMaterialReport;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.users.data.models.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class MaterialReportCaseMapper {

    public MaterialReportCaseListItemResponse toListItem(MaterialReportCase reportCase, UUID adminUserId) {
        AcademicMaterial material = reportCase.getAcademicMaterial();
        User assignedAdmin = reportCase.getAssignedAdmin();

        AssignedAdminResponse assignedAdminResponse = assignedAdmin == null
                ? null
                : new AssignedAdminResponse(assignedAdmin.getUserId(), assignedAdmin.getFullName());

        boolean assignedToMe = assignedAdmin != null && adminUserId != null
                && adminUserId.equals(assignedAdmin.getUserId());

        return new MaterialReportCaseListItemResponse(
                reportCase.getMaterialReportCaseId(),
                material.getAcademicMaterialId(),
                fileName(material),
                material.getMaterialType(),
                material.getTutorSubject().getSubject().getName(),
                material.getTutorSubject().getTutorProfile().getUser().getFullName(),
                reportCase.getReportsCount(),
                reportCase.getStatus(),
                reportCase.getOpenedAt(),
                assignedAdminResponse,
                assignedToMe,
                reportCase.getResolvedAt(),
                reportCase.getResolutionNote());
    }

    public MaterialReportCaseDetailResponse toDetail(
            MaterialReportCase reportCase,
            List<AcademicMaterialReport> reports,
            UUID adminUserId) {

        AcademicMaterial material = reportCase.getAcademicMaterial();
        TutorProfile tutorProfile = material.getTutorSubject().getTutorProfile();
        User tutorUser = tutorProfile.getUser();

        TutorOwnerResponse tutor = new TutorOwnerResponse(
                tutorUser.getFullName(),
                tutorProfile.getCareer() == null ? null : tutorProfile.getCareer().getName(),
                tutorUser.getDni(),
                tutorProfile.getInstitutionalId());

        MaterialInfoResponse materialInfo = new MaterialInfoResponse(
                fileName(material),
                material.getMaterialType(),
                material.getSizeInBytes(),
                material.getUploadedAt());

        List<MaterialReportItemResponse> reportItems = reports.stream()
                .map(report -> new MaterialReportItemResponse(
                        report.getReason(),
                        report.getReportedAt(),
                        report.getDescription()))
                .toList();

        boolean canTake = reportCase.getStatus() == MaterialReportCaseStatus.PENDING;
        boolean canResolve = reportCase.getStatus() == MaterialReportCaseStatus.IN_REVIEW
                && reportCase.getAssignedAdmin() != null
                && adminUserId != null
                && adminUserId.equals(reportCase.getAssignedAdmin().getUserId());

        return new MaterialReportCaseDetailResponse(
                toListItem(reportCase, adminUserId),
                tutor,
                materialInfo,
                reportItems,
                new PermissionsResponse(canTake, canResolve));
    }

    private String fileName(AcademicMaterial material) {
        String originalFileName = material.getOriginalFileName();
        return originalFileName != null && !originalFileName.isBlank()
                ? originalFileName
                : material.getName();
    }
}