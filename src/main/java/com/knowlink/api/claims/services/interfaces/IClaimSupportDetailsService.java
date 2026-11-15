package com.knowlink.api.claims.services.interfaces;

import com.knowlink.api.claims.controllers.responses.ClaimAttachmentUrlResponse;
import com.knowlink.api.claims.controllers.responses.ClaimCommentResponse;
import com.knowlink.api.claims.controllers.responses.ClaimFundsSummaryResponse;
import com.knowlink.api.claims.controllers.responses.ClaimResolutionResponse;
import com.knowlink.api.claims.controllers.responses.ClaimReviewProgressResponse;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.claims.data.models.SessionClaim;
import com.knowlink.api.claims.data.models.SessionClaimComment;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.security.enums.Role;

import java.util.List;
import java.util.UUID;

public interface IClaimSupportDetailsService {

    ClaimFundsSummaryResponse getFundsSummary(Booking booking, ClaimStatus claimStatus);

    ClaimReviewProgressResponse getReviewProgress(SessionClaim claim);

    /**
     * Resolution of the claim for the given viewer. Returns {@code null} when the claim is not
     * resolved yet or when it was resolved without resolution data.
     */
    ClaimResolutionResponse getResolution(SessionClaim claim, Role viewerRole);

    List<ClaimAttachmentUrlResponse> getAttachments(UUID claimId);

    List<ClaimCommentResponse> getComments(UUID claimId);

    ClaimCommentResponse toCommentResponse(SessionClaimComment comment);
}
