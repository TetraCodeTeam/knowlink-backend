package com.knowlink.api.admin.materialreports.services.interfaces;

import com.knowlink.api.materials.data.enums.MaterialReportReason;
import com.knowlink.api.materials.data.models.AcademicMaterialReport;

import java.util.UUID;

public interface IMaterialReportIntakeService {

    /**
     * Registers a report: adds it to the material's open case or opens a new PENDING case.
     */
    AcademicMaterialReport registerReport(UUID materialId, UUID reporterUserId,
                                          MaterialReportReason reason, String description);
}