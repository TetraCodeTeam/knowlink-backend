package com.knowlink.api.materials.service.implementations;

import com.knowlink.api.exceptions.custom_exceptions.DuplicateResourceException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.materials.data.mappers.AcademicMaterialReportMapper;
import com.knowlink.api.materials.data.models.AcademicMaterial;
import com.knowlink.api.materials.data.models.AcademicMaterialReport;
import com.knowlink.api.materials.repositories.IAcademicMaterialReportRepository;
import com.knowlink.api.materials.repositories.IAcademicMaterialRepository;
import com.knowlink.api.materials.controller.requests.MaterialReportRequest;
import com.knowlink.api.materials.controller.responses.MaterialReportResponse;
import com.knowlink.api.materials.exception.ResourceAccessDeniedException;
import com.knowlink.api.materials.service.interfaces.IMaterialAccessService;
import com.knowlink.api.materials.service.interfaces.IMaterialReportService;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaterialReportServiceImpl implements IMaterialReportService {

    private static final String DUPLICATE_CODE = "MATERIAL_ALREADY_REPORTED";
    private static final String DUPLICATE_MESSAGE = "Ya reportaste este material anteriormente";
    private static final String SUBMITTED_MESSAGE = "Tu reporte fue enviado correctamente.";

    private final IAcademicMaterialRepository materialRepository;
    private final IAcademicMaterialReportRepository reportRepository;
    private final IUserRepository userRepository;
    private final IMaterialAccessService materialAccessService;
    private final AcademicMaterialReportMapper reportMapper;

    @Override
    @Transactional
    public MaterialReportResponse reportMaterial(UUID studentId, UUID materialId, MaterialReportRequest request) {
        AcademicMaterial material = materialRepository.findByAcademicMaterialIdAndActiveTrueAndAvailableTrue(materialId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MATERIAL_NOT_FOUND",
                        "El material no existe.",
                        "Active material not found for report: " + materialId));

        UUID tutorUserId = material.getTutorSubject().getTutorProfile().getUser().getUserId();
        if (tutorUserId.equals(studentId)) {
            throw new ResourceAccessDeniedException("No podés denunciar tu propio material.");
        }

        materialAccessService.validateAccessOrDeny(studentId, materialId);
        if (reportRepository.existsByMaterialAcademicMaterialIdAndReporterUserId(materialId, studentId)) {
            throw duplicateReport(studentId, materialId);
        }

        User reporter = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "USER_NOT_FOUND",
                        "El usuario no existe.",
                        "User not found while reporting material: " + studentId));
        AcademicMaterialReport report = reportMapper.toEntity(material, reporter, request);

        try {
            reportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateReport(studentId, materialId);
        }

        if (materialRepository.incrementReportsCount(materialId) != 1) {
            throw new ResourceNotFoundException(
                    "MATERIAL_NOT_FOUND",
                    "El material no existe.",
                    "Active material disappeared while report was being recorded: " + materialId);
        }

        return new MaterialReportResponse(SUBMITTED_MESSAGE);
    }

    private DuplicateResourceException duplicateReport(UUID studentId, UUID materialId) {
        String technicalMessage = "User " + studentId + " already reported material " + materialId;
        return new DuplicateResourceException(DUPLICATE_CODE, DUPLICATE_MESSAGE, technicalMessage);
    }
}