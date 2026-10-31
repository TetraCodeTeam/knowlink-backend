package com.knowlink.api.admin.materialreports.controllers.responses;

public record StatusCountsResponse(long pending, long inReview, long removed, long dismissed) {
}