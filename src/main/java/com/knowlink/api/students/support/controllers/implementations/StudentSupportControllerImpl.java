package com.knowlink.api.students.support.controllers.implementations;

import com.knowlink.api.claims.controllers.requests.AddClaimCommentRequest;
import com.knowlink.api.claims.controllers.responses.ClaimCommentResponse;
import com.knowlink.api.materials.controller.responses.MaterialReportingPolicyResponse;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.students.support.controllers.interfaces.IStudentSupportController;
import com.knowlink.api.students.support.controllers.responses.StudentMaterialReportDetailResponse;
import com.knowlink.api.students.support.controllers.responses.StudentSessionClaimDetailResponse;
import com.knowlink.api.students.support.controllers.responses.StudentSupportResponse;
import com.knowlink.api.students.support.services.interfaces.IStudentSupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class StudentSupportControllerImpl implements IStudentSupportController {

    private final IStudentSupportService studentSupportService;

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public StudentSupportResponse getSupport(UserPrincipal principal) {
        return studentSupportService.getSupport(principal.getUser().getUserId());
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public MaterialReportingPolicyResponse getMaterialReportingPolicy(UserPrincipal principal) {
        return studentSupportService.getMaterialReportingPolicy();
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public StudentSessionClaimDetailResponse getSessionClaimDetails(UserPrincipal principal, UUID claimId) {
        return studentSupportService.getSessionClaimDetails(principal.getUser().getUserId(), claimId);
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public ClaimCommentResponse addClaimComment(
            UserPrincipal principal, UUID claimId, AddClaimCommentRequest request) {
        return studentSupportService.addClaimComment(principal.getUser().getUserId(), claimId, request);
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public StudentMaterialReportDetailResponse getMaterialReportDetails(UserPrincipal principal, UUID reportId) {
        return studentSupportService.getMaterialReportDetails(principal.getUser().getUserId(), reportId);
    }
}
