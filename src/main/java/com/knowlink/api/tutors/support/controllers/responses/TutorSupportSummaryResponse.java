package com.knowlink.api.tutors.support.controllers.responses;

public record TutorSupportSummaryResponse(
        int sessionClaimCount,
        int reportedMaterialCount,
        int materialsInReviewCount) {
}
