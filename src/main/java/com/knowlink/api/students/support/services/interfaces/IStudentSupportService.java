package com.knowlink.api.students.support.services.interfaces;

import com.knowlink.api.claims.controllers.requests.AddClaimCommentRequest;
import com.knowlink.api.claims.controllers.responses.ClaimCommentResponse;
import com.knowlink.api.materials.controller.responses.MaterialReportingPolicyResponse;
import com.knowlink.api.students.support.controllers.responses.StudentMaterialReportDetailResponse;
import com.knowlink.api.students.support.controllers.responses.StudentSessionClaimDetailResponse;
import com.knowlink.api.students.support.controllers.responses.StudentSupportResponse;

import java.util.UUID;

public interface IStudentSupportService {

    StudentSupportResponse getSupport(UUID studentUserId);

    MaterialReportingPolicyResponse getMaterialReportingPolicy();

    StudentSessionClaimDetailResponse getSessionClaimDetails(UUID studentUserId, UUID claimId);

    ClaimCommentResponse addClaimComment(UUID studentUserId, UUID claimId, AddClaimCommentRequest request);

    StudentMaterialReportDetailResponse getMaterialReportDetails(UUID studentUserId, UUID reportId);
}
