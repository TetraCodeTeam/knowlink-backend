package com.knowlink.api.tutors.support.controllers.requests;

import com.knowlink.api.materials.data.enums.MaterialReportStatus;
import com.knowlink.api.materials.data.enums.MaterialReportResolution;
import jakarta.validation.constraints.NotNull;

public record UpdateMaterialReportStatusRequest(
        @NotNull MaterialReportStatus status,
        MaterialReportResolution resolution) {
}
