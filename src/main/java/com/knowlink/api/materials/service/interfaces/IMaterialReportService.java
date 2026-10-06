package com.knowlink.api.materials.service.interfaces;

import com.knowlink.api.materials.controller.requests.MaterialReportRequest;
import com.knowlink.api.materials.controller.responses.MaterialReportResponse;

import java.util.UUID;

public interface IMaterialReportService {
    MaterialReportResponse reportMaterial(UUID studentId, UUID materialId, MaterialReportRequest request);
}