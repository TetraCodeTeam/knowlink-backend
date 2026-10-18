package com.knowlink.api.tutors.support.controllers.responses;

import java.util.List;

public record TutorSupportResponse(
        TutorSupportSummaryResponse summary,
        List<TutorSupportClaimResponse> sessionClaims,
        List<TutorReportedMaterialResponse> reportedMaterials) {
}
