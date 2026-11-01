package com.knowlink.api.admin.materialreports.controllers.implementations;

import com.knowlink.api.admin.materialreports.controllers.interfaces.IAdminMaterialReportController;
import com.knowlink.api.admin.materialreports.controllers.requests.RemoveMaterialRequest;
import com.knowlink.api.admin.materialreports.controllers.responses.DownloadUrlResponse;
import com.knowlink.api.admin.materialreports.controllers.responses.MaterialReportCaseDetailResponse;
import com.knowlink.api.admin.materialreports.controllers.responses.MaterialReportCaseListResponse;
import com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus;
import com.knowlink.api.admin.materialreports.services.interfaces.IAdminMaterialDownloadService;
import com.knowlink.api.admin.materialreports.services.interfaces.IAdminMaterialReportService;
import com.knowlink.api.security.models.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMaterialReportControllerImpl implements IAdminMaterialReportController {

    private final IAdminMaterialReportService adminMaterialReportService;
    private final IAdminMaterialDownloadService adminMaterialDownloadService;

    @Override
    public ResponseEntity<MaterialReportCaseListResponse> list(MaterialReportCaseStatus status,
            boolean assignedToMe, LocalDate from, LocalDate to, Integer page, Integer size,
            UserPrincipal principal) {
        UUID adminUserId = currentAdminId(principal);
        return ResponseEntity.ok(adminMaterialReportService.listCases(
                status, assignedToMe, from, to, page, size, adminUserId));
    }

    @Override
    public ResponseEntity<MaterialReportCaseDetailResponse> detail(UUID caseId, UserPrincipal principal) {
        return ResponseEntity.ok(adminMaterialReportService.getCaseDetail(caseId, currentAdminId(principal)));
    }

    @Override
    public ResponseEntity<MaterialReportCaseDetailResponse> assign(UUID caseId, UserPrincipal principal) {
        return ResponseEntity.ok(adminMaterialReportService.assignCase(caseId, currentAdminId(principal)));
    }

    @Override
    public ResponseEntity<MaterialReportCaseDetailResponse> remove(UUID caseId, RemoveMaterialRequest request,
            UserPrincipal principal) {
        String reason = request == null ? null : request.reason();
        return ResponseEntity.ok(adminMaterialReportService.removeMaterial(
                caseId, reason, currentAdminId(principal)));
    }

    @Override
    public ResponseEntity<MaterialReportCaseDetailResponse> dismiss(UUID caseId, UserPrincipal principal) {
        return ResponseEntity.ok(adminMaterialReportService.dismissCase(caseId, currentAdminId(principal)));
    }

    @Override
    public ResponseEntity<DownloadUrlResponse> downloadUrl(UUID caseId, UserPrincipal principal) {
        return ResponseEntity.ok(adminMaterialDownloadService.generateDownloadUrl(
                caseId, currentAdminId(principal)));
    }

    private UUID currentAdminId(UserPrincipal principal) {
        return principal.getUser().getUserId();
    }
}