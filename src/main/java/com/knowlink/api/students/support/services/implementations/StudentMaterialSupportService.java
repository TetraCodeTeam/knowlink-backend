package com.knowlink.api.students.support.services.implementations;

import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.materials.controller.responses.MaterialReportingPolicyResponse;
import com.knowlink.api.materials.data.models.AcademicMaterialReport;
import com.knowlink.api.materials.policy.MaterialReportingPolicyProvider;
import com.knowlink.api.materials.repositories.IAcademicMaterialReportRepository;
import com.knowlink.api.students.support.controllers.responses.StudentMaterialReportDetailResponse;
import com.knowlink.api.students.support.controllers.responses.StudentMaterialReportResponse;
import com.knowlink.api.students.support.data.mappers.StudentSupportMapper;
import com.knowlink.api.students.support.services.interfaces.IStudentMaterialSupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentMaterialSupportService implements IStudentMaterialSupportService {

    private final IAcademicMaterialReportRepository materialReportRepository;
    private final StudentSupportMapper studentSupportMapper;
    private final MaterialReportingPolicyProvider materialReportingPolicyProvider;

    @Override
    @Transactional(readOnly = true)
    public MaterialReportingPolicyResponse getMaterialReportingPolicy() {
        return materialReportingPolicyProvider.getPolicy();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentMaterialReportResponse> getReports(UUID studentUserId) {
        return materialReportRepository.findStudentReports(studentUserId).stream()
                .map(studentSupportMapper::toMaterialReportResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StudentMaterialReportDetailResponse getReportDetails(UUID studentUserId, UUID reportId) {
        AcademicMaterialReport report = materialReportRepository
                .findStudentReportDetails(reportId, studentUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MATERIAL_REPORT_NOT_FOUND",
                        "La denuncia no existe.",
                        "Material report " + reportId + " not found for student " + studentUserId));
        return studentSupportMapper.toMaterialReportDetails(report);
    }
}
