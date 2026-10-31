package com.knowlink.api.admin.materialreports.controllers.responses;

import com.knowlink.api.shared.responses.PagedResponse;

public record MaterialReportCaseListResponse(
        PagedResponse<MaterialReportCaseListItemResponse> page,
        StatusCountsResponse statusCounts
) {
}