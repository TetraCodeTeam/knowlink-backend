package com.knowlink.api.admin.materialreports.controllers.responses;

import java.util.List;

public record MaterialReportCaseDetailResponse(
        MaterialReportCaseListItemResponse summary,
        TutorOwnerResponse tutor,
        MaterialInfoResponse material,
        List<MaterialReportItemResponse> reports,
        PermissionsResponse permissions
) {
}