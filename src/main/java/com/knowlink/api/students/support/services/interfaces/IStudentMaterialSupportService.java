package com.knowlink.api.students.support.services.interfaces;

import com.knowlink.api.materials.controller.responses.MaterialReportingPolicyResponse;
import com.knowlink.api.students.support.controllers.responses.StudentMaterialReportDetailResponse;
import com.knowlink.api.students.support.controllers.responses.StudentMaterialReportResponse;

import java.util.List;
import java.util.UUID;

public interface IStudentMaterialSupportService {

    MaterialReportingPolicyResponse getMaterialReportingPolicy();

    List<StudentMaterialReportResponse> getReports(UUID studentUserId);

    StudentMaterialReportDetailResponse getReportDetails(UUID studentUserId, UUID reportId);
}
