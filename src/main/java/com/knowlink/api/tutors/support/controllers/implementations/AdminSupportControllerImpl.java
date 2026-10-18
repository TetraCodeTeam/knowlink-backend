package com.knowlink.api.tutors.support.controllers.implementations;

import com.knowlink.api.claims.services.interfaces.ISessionClaimService;
import com.knowlink.api.materials.service.interfaces.IMaterialReportService;
import com.knowlink.api.tutors.support.controllers.interfaces.IAdminSupportController;
import com.knowlink.api.tutors.support.controllers.requests.UpdateClaimStatusRequest;
import com.knowlink.api.tutors.support.controllers.requests.UpdateMaterialReportStatusRequest;
import com.knowlink.api.tutors.support.controllers.responses.SupportStatusResponse;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.tutors.support.controllers.responses.TutorReportedMaterialDetailResponse;
import com.knowlink.api.tutors.support.services.interfaces.ITutorSupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AdminSupportControllerImpl implements IAdminSupportController {

    private final ISessionClaimService sessionClaimService;
    private final IMaterialReportService materialReportService;
    private final ITutorSupportService tutorSupportService;

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public TutorReportedMaterialDetailResponse getReportedMaterialDetails(
            UserPrincipal principal, UUID materialId) {
        return tutorSupportService.getAdminReportedMaterialDetails(materialId);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public SupportStatusResponse updateClaimStatus(UUID claimId, UpdateClaimStatusRequest request) {
        sessionClaimService.updateStatus(claimId, request.status());
        return new SupportStatusResponse(request.status().name());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public SupportStatusResponse updateMaterialReportStatus(
            UserPrincipal principal, UUID reportId, UpdateMaterialReportStatusRequest request) {
        materialReportService.updateStatus(
                reportId, request.status(), request.resolution(), principal.getUser().getUserId());
        return new SupportStatusResponse(request.status().name());
    }
}
