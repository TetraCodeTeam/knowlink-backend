package com.knowlink.api.materials.service.interfaces;

import com.knowlink.api.materials.controller.requests.MaterialReportRequest;
import com.knowlink.api.materials.controller.responses.MaterialReportResponse;
import com.knowlink.api.materials.data.enums.MaterialReportStatus;
import com.knowlink.api.materials.data.enums.MaterialReportResolution;

import java.util.UUID;

public interface IMaterialReportService {
    MaterialReportResponse reportMaterial(UUID studentId, UUID materialId, MaterialReportRequest request);

    void updateStatus(
            UUID reportId,
            MaterialReportStatus status,
            MaterialReportResolution resolution,
            UUID reviewerUserId);
}
