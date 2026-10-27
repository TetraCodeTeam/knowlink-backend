package com.knowlink.api.students.support.controllers.responses;

import java.util.List;

public record StudentSupportResponse(
        StudentSupportSummaryResponse summary,
        List<StudentSessionClaimResponse> sessionClaims,
        List<StudentMaterialReportResponse> reportedMaterials) {}
