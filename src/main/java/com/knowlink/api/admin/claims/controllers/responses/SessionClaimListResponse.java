package com.knowlink.api.admin.claims.controllers.responses;

import com.knowlink.api.shared.responses.PagedResponse;

public record SessionClaimListResponse(
        PagedResponse<SessionClaimListItemResponse> page,
        ClaimStatusCountsResponse statusCounts) {
}