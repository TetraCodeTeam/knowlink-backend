package com.knowlink.api.claims.services.interfaces;

import com.knowlink.api.claims.controllers.responses.ClaimAttachmentUrlResponse;
import com.knowlink.api.claims.controllers.responses.ClaimCommentResponse;
import com.knowlink.api.claims.controllers.responses.ClaimFundsSummaryResponse;
import com.knowlink.api.claims.controllers.responses.ClaimReviewProgressResponse;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.claims.data.models.SessionClaim;
import com.knowlink.api.claims.data.models.SessionClaimComment;
import com.knowlink.api.bookings.data.models.Booking;

import java.util.List;
import java.util.UUID;

public interface IClaimSupportDetailsService {

    ClaimFundsSummaryResponse getFundsSummary(Booking booking, ClaimStatus claimStatus);

    ClaimReviewProgressResponse getReviewProgress(SessionClaim claim);

    List<ClaimAttachmentUrlResponse> getAttachments(UUID claimId);

    List<ClaimCommentResponse> getComments(UUID claimId);

    ClaimCommentResponse toCommentResponse(SessionClaimComment comment);
}
