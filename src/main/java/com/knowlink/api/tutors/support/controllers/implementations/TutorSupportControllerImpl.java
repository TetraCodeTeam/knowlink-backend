package com.knowlink.api.tutors.support.controllers.implementations;

import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.tutors.support.controllers.interfaces.ITutorSupportController;
import com.knowlink.api.tutors.support.controllers.responses.TutorSupportResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorSupportClaimDetailResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorReportedMaterialDetailResponse;
import com.knowlink.api.tutors.support.controllers.requests.SubmitMaterialDefenseRequest;
import com.knowlink.api.claims.controllers.requests.AddClaimCommentRequest;
import com.knowlink.api.claims.controllers.responses.ClaimCommentResponse;
import com.knowlink.api.materials.controller.responses.MaterialReportingPolicyResponse;
import com.knowlink.api.tutors.support.services.interfaces.ITutorSupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class TutorSupportControllerImpl implements ITutorSupportController {

    private final ITutorSupportService tutorSupportService;

    @Override
    @PreAuthorize("hasRole('TUTOR')")
    public TutorSupportResponse getSupport(UserPrincipal principal) {
        return tutorSupportService.getSupport(principal.getUser().getUserId());
    }

    @Override
    @PreAuthorize("hasRole('TUTOR')")
    public MaterialReportingPolicyResponse getMaterialReportingPolicy(UserPrincipal principal) {
        return tutorSupportService.getMaterialReportingPolicy();
    }

    @Override
    @PreAuthorize("hasRole('TUTOR')")
    public TutorSupportClaimDetailResponse getClaimDetails(UserPrincipal principal, UUID claimId) {
        return tutorSupportService.getClaimDetails(principal.getUser().getUserId(), claimId);
    }

    @Override
    @PreAuthorize("hasRole('TUTOR')")
    public ClaimCommentResponse addClaimComment(
            UserPrincipal principal, UUID claimId, AddClaimCommentRequest request) {
        return tutorSupportService.addClaimComment(principal.getUser().getUserId(), claimId, request);
    }

    @Override
    @PreAuthorize("hasRole('TUTOR')")
    public TutorReportedMaterialDetailResponse getReportedMaterialDetails(
            UserPrincipal principal, UUID materialId) {
        return tutorSupportService.getReportedMaterialDetails(principal.getUser().getUserId(), materialId);
    }

    @Override
    @PreAuthorize("hasRole('TUTOR')")
    public TutorReportedMaterialDetailResponse submitMaterialDefense(
            UserPrincipal principal,
            UUID materialId,
            SubmitMaterialDefenseRequest request,
            List<MultipartFile> files) {
        return tutorSupportService.submitMaterialDefense(
                principal.getUser().getUserId(), materialId, request, files);
    }
}
