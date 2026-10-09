package com.knowlink.api.materials.data.mappers;

import com.knowlink.api.materials.data.models.AcademicMaterial;
import com.knowlink.api.materials.data.models.AcademicMaterialReport;
import com.knowlink.api.materials.controller.requests.MaterialReportRequest;
import com.knowlink.api.users.data.models.User;
import org.springframework.stereotype.Component;

@Component
public class AcademicMaterialReportMapper {

    public AcademicMaterialReport toEntity(AcademicMaterial material, User reporter, MaterialReportRequest request) {
        return AcademicMaterialReport.builder()
                .material(material)
                .reporter(reporter)
                .reason(request.reason())
                .description(request.description())
                .build();
    }
}