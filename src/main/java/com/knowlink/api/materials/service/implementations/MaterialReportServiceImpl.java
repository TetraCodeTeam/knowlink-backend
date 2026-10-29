package com.knowlink.api.materials.service.implementations;

import com.knowlink.api.admin.materialreports.services.interfaces.IMaterialReportIntakeService;
import com.knowlink.api.exceptions.custom_exceptions.DuplicateResourceException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.materials.data.enums.MaterialReportStatus;
import com.knowlink.api.materials.data.enums.MaterialReportResolution;
import com.knowlink.api.materials.data.enums.MaterialReportReason;
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
import com.knowlink.api.notifications.data.enums.NotificationType;
import com.knowlink.api.notifications.services.interfaces.INotificationService;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class MaterialReportServiceImpl implements IMaterialReportService {

    private static final String DUPLICATE_CODE = "MATERIAL_ALREADY_REPORTED";
    private static final String DUPLICATE_MESSAGE = "Ya reportaste este material anteriormente";
    private static final String SUBMITTED_MESSAGE = "Tu reporte fue enviado correctamente.";

    private final IAcademicMaterialRepository materialRepository;
    private final IAcademicMaterialReportRepository reportRepository;
    private final IUserRepository userRepository;
    private final IMaterialAccessService materialAccessService;
    private final AcademicMaterialReportMapper reportMapper;
    private final INotificationService notificationService;
    private final IMaterialReportIntakeService intakeService;

    @Autowired
    public MaterialReportServiceImpl(
            IAcademicMaterialRepository materialRepository,
            IAcademicMaterialReportRepository reportRepository,
            IUserRepository userRepository,
            IMaterialAccessService materialAccessService,
            AcademicMaterialReportMapper reportMapper,
            INotificationService notificationService,
            IMaterialReportIntakeService intakeService) {
        this.materialRepository = materialRepository;
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.materialAccessService = materialAccessService;
        this.reportMapper = reportMapper;
        this.notificationService = notificationService;
        this.intakeService = intakeService;
    }

    /**
     * Backward-compatible constructor used by unit tests that predate the report-case intake service.
     * Production wiring always uses the constructor above.
     */
    public MaterialReportServiceImpl(
            IAcademicMaterialRepository materialRepository,
            IAcademicMaterialReportRepository reportRepository,
            IUserRepository userRepository,
            IMaterialAccessService materialAccessService,
            AcademicMaterialReportMapper reportMapper,
            INotificationService notificationService) {
        this(materialRepository, reportRepository, userRepository, materialAccessService, reportMapper,
                notificationService, (materialId, reporterUserId, reason, description) -> {
                    AcademicMaterial material = materialRepository
                            .findByAcademicMaterialIdAndActiveTrue(materialId)
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "MATERIAL_NOT_FOUND",
                                    "El material no existe.",
                                    "Active material not found for report: " + materialId));
                    User reporter = userRepository.findById(reporterUserId)
                            .orElseThrow(() -> new ResourceNotFoundException(
                                    "USER_NOT_FOUND",
                                    "El usuario no existe.",
                                    "User not found while reporting material: " + reporterUserId));
                    AcademicMaterialReport report = AcademicMaterialReport.builder()
                            .material(material)
                            .reporter(reporter)
                            .reason(reason)
                            .description(description)
                            .build();
                    try {
                        return reportRepository.saveAndFlush(report);
                    } catch (DataIntegrityViolationException exception) {
                        throw new DuplicateResourceException(DUPLICATE_CODE, DUPLICATE_MESSAGE,
                                "User " + reporterUserId + " already reported material " + materialId);
                    }
                });
    }

    @Override
    @Transactional
    public MaterialReportResponse reportMaterial(UUID studentId, UUID materialId, MaterialReportRequest request) {
        AcademicMaterial material = materialRepository.findByAcademicMaterialIdAndActiveTrue(materialId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MATERIAL_NOT_FOUND",
                        "El material no existe.",
                        "Active material not found for report: " + materialId));

        boolean reportUnderReview = reportRepository.findDetailsByMaterialId(materialId).stream()
                .anyMatch(existingReport -> MaterialReportStatus.orPending(existingReport.getStatus())
                        != MaterialReportStatus.RESOLVED);
        if (!material.isAvailable() && !reportUnderReview) {
            throw new ResourceAccessDeniedException("No podés denunciar un material que ya no está disponible.");
        }

        UUID tutorUserId = material.getTutorSubject().getTutorProfile().getUser().getUserId();
        if (tutorUserId.equals(studentId)) {
            throw new ResourceAccessDeniedException("No podés denunciar tu propio material.");
        }

        materialAccessService.validateReportAccessOrDeny(studentId, materialId);
        if (request.reason() == MaterialReportReason.OTHER
                && (request.description() == null || request.description().isBlank())) {
            throw new ValidationException("Describí el motivo de la denuncia.");
        }
        if (reportRepository.existsByMaterialAcademicMaterialIdAndReporterUserId(materialId, studentId)) {
            throw duplicateReport(studentId, materialId);
        }

        try {
            intakeService.registerReport(materialId, studentId, request.reason(), request.description());
        } catch (DataIntegrityViolationException exception) {
            throw duplicateReport(studentId, materialId);
        }

        if (materialRepository.incrementReportsCount(materialId) != 1) {
            throw new ResourceNotFoundException(
                    "MATERIAL_NOT_FOUND",
                    "El material no existe.",
                    "Active material disappeared while report was being recorded: " + materialId);
        }

        if (materialRepository.markUnavailable(materialId) != 1) {
            throw new ResourceNotFoundException(
                    "MATERIAL_NOT_FOUND",
                    "El material no existe.",
                    "Active material disappeared while being suspended for review: " + materialId);
        }

        notificationService.createSupportNotification(
                tutorUserId,
                Role.TUTOR,
                NotificationType.MATERIAL_REPORT_UPDATE,
                material.getAcademicMaterialId(),
                "Denuncia de material recibida",
                "Tu material " + material.getName() + " recibió una denuncia.");

        return new MaterialReportResponse(SUBMITTED_MESSAGE);
    }

    @Override
    @Transactional
    public void updateStatus(
            UUID reportId,
            MaterialReportStatus status,
            MaterialReportResolution resolution,
            UUID reviewerUserId) {
        AcademicMaterialReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MATERIAL_REPORT_NOT_FOUND",
                        "La denuncia no existe.",
                        "Material report not found for id: " + reportId));

        MaterialReportStatus currentStatus = MaterialReportStatus.orPending(report.getStatus());
        if (!currentStatus.canTransitionTo(status)) {
            throw new ValidationException("No se puede cambiar el estado de la denuncia.");
        }
        if ((status == MaterialReportStatus.RESOLVED) != (resolution != null)) {
            throw new ValidationException(
                    "Al resolver la denuncia, indicá si se confirma o se desestima el incumplimiento.");
        }
        if (currentStatus == status) {
            return;
        }

        report.setStatus(status);
        if (report.getReviewedBy() == null) {
            report.setReviewedBy(userRepository.findById(reviewerUserId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "USER_NOT_FOUND",
                            "El usuario no existe.",
                            "Reviewer not found: " + reviewerUserId)));
        }
        report.setResolution(resolution);
        reportRepository.saveAndFlush(report);

        var material = report.getMaterial();
        var allReports = reportRepository.findDetailsByMaterialId(material.getAcademicMaterialId());
        boolean allResolved = allReports.stream().allMatch(item -> item.getStatus() == MaterialReportStatus.RESOLVED);
        boolean anyUpheld = allReports.stream()
                .anyMatch(item -> item.getResolution() == MaterialReportResolution.UPHELD);
        if (allResolved) {
            material.setAvailable(!anyUpheld);
        } else {
            material.setAvailable(false);
        }
        materialRepository.save(material);
        notificationService.createSupportNotification(
                report.getMaterial().getTutorSubject().getTutorProfile().getUser().getUserId(),
                Role.TUTOR,
                NotificationType.MATERIAL_REPORT_UPDATE,
                report.getMaterial().getAcademicMaterialId(),
                "Actualización de denuncia",
                "El estado de la denuncia de tu material " + report.getMaterial().getName()
                        + " cambió a " + status.displayLabel() + ".");
        notificationService.createSupportNotification(
                report.getReporter().getUserId(),
                Role.STUDENT,
                NotificationType.MATERIAL_REPORT_UPDATE,
                report.getAcademicMaterialReportId(),
                status == MaterialReportStatus.RESOLVED
                        ? "Resultado de tu denuncia"
                        : "Actualización de tu denuncia",
                studentStatusMessage(report, status, resolution));
    }

    private String studentStatusMessage(
            AcademicMaterialReport report,
            MaterialReportStatus status,
            MaterialReportResolution resolution) {
        if (status == MaterialReportStatus.RESOLVED) {
            return resolution == MaterialReportResolution.UPHELD
                    ? "La revisión confirmó tu denuncia sobre " + report.getMaterial().getName()
                            + ". El material dejó de estar disponible."
                    : "La revisión desestimó tu denuncia sobre " + report.getMaterial().getName()
                            + ". El material continúa disponible.";
        }

        return "Tu denuncia sobre " + report.getMaterial().getName()
                + " cambió a estado " + status.displayLabel() + ".";
    }

    private DuplicateResourceException duplicateReport(UUID studentId, UUID materialId) {
        String technicalMessage = "User " + studentId + " already reported material " + materialId;
        return new DuplicateResourceException(DUPLICATE_CODE, DUPLICATE_MESSAGE, technicalMessage);
    }
}