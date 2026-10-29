package com.knowlink.api.admin.materialreports.services.implementations;

import com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus;
import com.knowlink.api.admin.materialreports.data.models.MaterialReportCase;
import com.knowlink.api.admin.materialreports.repositories.IMaterialReportCaseRepository;
import com.knowlink.api.admin.materialreports.services.interfaces.IMaterialReportIntakeService;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.materials.data.enums.MaterialReportReason;
import com.knowlink.api.materials.data.models.AcademicMaterial;
import com.knowlink.api.materials.data.models.AcademicMaterialReport;
import com.knowlink.api.materials.repositories.IAcademicMaterialReportRepository;
import com.knowlink.api.materials.repositories.IAcademicMaterialRepository;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaterialReportIntakeServiceImpl implements IMaterialReportIntakeService {

    private final IAcademicMaterialRepository materialRepository;
    private final IAcademicMaterialReportRepository reportRepository;
    private final IMaterialReportCaseRepository caseRepository;
    private final IUserRepository userRepository;
    private final Clock clock;

    @Override
    @Transactional
    public AcademicMaterialReport registerReport(UUID materialId, UUID reporterUserId,
                                                 MaterialReportReason reason, String description) {

        AcademicMaterial material = materialRepository.findByIdForUpdate(materialId)
                .filter(AcademicMaterial::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MATERIAL_NOT_FOUND",
                        "El material no existe.",
                        "Active material not found for report: " + materialId));

        User reporter = userRepository.findById(reporterUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "USER_NOT_FOUND",
                        "El usuario no existe.",
                        "User not found while reporting material: " + reporterUserId));

        MaterialReportCase reportCase = caseRepository.findOpenCasesByMaterialId(materialId).stream()
                .findFirst()
                .orElse(null);

        if (reportCase == null) {
            reportCase = caseRepository.save(MaterialReportCase.builder()
                    .academicMaterial(material)
                    .status(MaterialReportCaseStatus.PENDING)
                    .openedAt(LocalDateTime.now(clock))
                    .reportsCount(1)
                    .build());
        } else {
            reportCase.setReportsCount(reportCase.getReportsCount() + 1);
            caseRepository.save(reportCase);
        }

        AcademicMaterialReport report = AcademicMaterialReport.builder()
                .material(material)
                .reportCase(reportCase)
                .reporter(reporter)
                .reason(reason)
                .description(description)
                .build();

        AcademicMaterialReport savedReport = reportRepository.saveAndFlush(report);

        log.info("Material report {} registered for material {} in case {}",
                savedReport.getAcademicMaterialReportId(), materialId, reportCase.getMaterialReportCaseId());

        return savedReport;
    }
}