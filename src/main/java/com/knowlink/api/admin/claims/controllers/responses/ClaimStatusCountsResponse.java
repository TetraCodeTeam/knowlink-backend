package com.knowlink.api.admin.claims.controllers.responses;

public record ClaimStatusCountsResponse(long open, long inReview, long resolved) {
}