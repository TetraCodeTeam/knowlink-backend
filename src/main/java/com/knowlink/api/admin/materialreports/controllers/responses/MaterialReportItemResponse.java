package com.knowlink.api.admin.materialreports.controllers.responses;

import com.knowlink.api.materials.data.enums.MaterialReportReason;

import java.time.LocalDateTime;

public record MaterialReportItemResponse(
        MaterialReportReason reason,
        LocalDateTime reportedAt,
        String description
) {
}