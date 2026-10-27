package com.knowlink.api.students.support.services.interfaces;

import com.knowlink.api.claims.controllers.requests.AddClaimCommentRequest;
import com.knowlink.api.claims.controllers.responses.ClaimCommentResponse;
import com.knowlink.api.students.support.controllers.responses.StudentSessionClaimDetailResponse;
import com.knowlink.api.students.support.controllers.responses.StudentSessionClaimResponse;

import java.util.List;
import java.util.UUID;

public interface IStudentSessionSupportService {

    List<StudentSessionClaimResponse> getClaims(UUID studentUserId);

    StudentSessionClaimDetailResponse getClaimDetails(UUID studentUserId, UUID claimId);

    ClaimCommentResponse addClaimComment(UUID studentUserId, UUID claimId, AddClaimCommentRequest request);
}
