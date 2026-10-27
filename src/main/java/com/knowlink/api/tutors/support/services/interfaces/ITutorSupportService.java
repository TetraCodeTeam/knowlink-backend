package com.knowlink.api.tutors.support.services.interfaces;

import com.knowlink.api.tutors.support.controllers.responses.TutorSupportResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorSupportClaimDetailResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorReportedMaterialDetailResponse;
import com.knowlink.api.tutors.support.controllers.requests.SubmitMaterialDefenseRequest;
import com.knowlink.api.claims.controllers.requests.AddClaimCommentRequest;
import com.knowlink.api.claims.controllers.responses.ClaimCommentResponse;
import com.knowlink.api.materials.controller.responses.MaterialReportingPolicyResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface ITutorSupportService {

    TutorSupportResponse getSupport(UUID tutorUserId);

    TutorSupportClaimDetailResponse getClaimDetails(UUID tutorUserId, UUID claimId);

    ClaimCommentResponse addClaimComment(UUID tutorUserId, UUID claimId, AddClaimCommentRequest request);

    TutorReportedMaterialDetailResponse getReportedMaterialDetails(UUID tutorUserId, UUID materialId);

    TutorReportedMaterialDetailResponse getAdminReportedMaterialDetails(UUID materialId);

    TutorReportedMaterialDetailResponse submitMaterialDefense(
            UUID tutorUserId,
            UUID materialId,
            SubmitMaterialDefenseRequest request,
            List<MultipartFile> files);

    MaterialReportingPolicyResponse getMaterialReportingPolicy();
}
