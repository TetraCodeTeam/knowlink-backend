package com.knowlink.api.tutors.support.services.interfaces;

import com.knowlink.api.claims.controllers.responses.ClaimCommentResponse;
import com.knowlink.api.claims.controllers.requests.AddClaimCommentRequest;
import com.knowlink.api.tutors.support.controllers.responses.TutorSupportClaimDetailResponse;
import com.knowlink.api.tutors.support.controllers.responses.TutorSupportClaimResponse;

import java.util.List;
import java.util.UUID;

public interface ITutorSessionSupportService {

    List<TutorSupportClaimResponse> getClaims(UUID tutorUserId);

    TutorSupportClaimDetailResponse getClaimDetails(UUID tutorUserId, UUID claimId);

    ClaimCommentResponse addClaimComment(UUID tutorUserId, UUID claimId, AddClaimCommentRequest request);
}
