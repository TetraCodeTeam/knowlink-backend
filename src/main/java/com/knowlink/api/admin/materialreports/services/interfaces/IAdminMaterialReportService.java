package com.knowlink.api.admin.materialreports.services.interfaces;

import com.knowlink.api.admin.materialreports.controllers.responses.MaterialReportCaseDetailResponse;
import com.knowlink.api.admin.materialreports.controllers.responses.MaterialReportCaseListResponse;
import com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus;

import java.time.LocalDate;
import java.util.UUID;

public interface IAdminMaterialReportService {

    MaterialReportCaseListResponse listCases(MaterialReportCaseStatus status, boolean assignedToMe,
            LocalDate from, LocalDate to, Integer page, Integer size, UUID adminUserId);

    MaterialReportCaseDetailResponse getCaseDetail(UUID caseId, UUID adminUserId);

    MaterialReportCaseDetailResponse assignCase(UUID caseId, UUID adminUserId);

    MaterialReportCaseDetailResponse removeMaterial(UUID caseId, String reason, UUID adminUserId);

    MaterialReportCaseDetailResponse dismissCase(UUID caseId, UUID adminUserId);
}