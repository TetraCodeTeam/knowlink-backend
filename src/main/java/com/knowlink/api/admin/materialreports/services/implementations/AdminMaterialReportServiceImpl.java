package com.knowlink.api.admin.materialreports.services.implementations;

import com.knowlink.api.admin.common.AdminPagination;
import com.knowlink.api.admin.common.DateRange;
import com.knowlink.api.admin.common.DateRangeResolver;
import com.knowlink.api.admin.common.audit.AdminAuditAction;
import com.knowlink.api.admin.common.audit.IAdminAuditService;
import com.knowlink.api.admin.materialreports.controllers.responses.MaterialReportCaseDetailResponse;
import com.knowlink.api.admin.materialreports.controllers.responses.MaterialReportCaseListItemResponse;
import com.knowlink.api.admin.materialreports.controllers.responses.MaterialReportCaseListResponse;
import com.knowlink.api.admin.materialreports.controllers.responses.StatusCountsResponse;
import com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus;
import com.knowlink.api.admin.materialreports.data.mappers.MaterialReportCaseMapper;
import com.knowlink.api.admin.materialreports.data.models.MaterialReportCase;
import com.knowlink.api.admin.materialreports.events.MaterialReportResolvedEvent;
import com.knowlink.api.admin.materialreports.repositories.IMaterialReportCaseRepository;
import com.knowlink.api.admin.materialreports.services.interfaces.IAdminMaterialReportService;
import com.knowlink.api.exceptions.custom_exceptions.BusinessConflictException;
import com.knowlink.api.exceptions.custom_exceptions.NotAssignedToAdminException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.materials.data.enums.MaterialReportResolution;
import com.knowlink.api.materials.data.enums.MaterialReportStatus;
import com.knowlink.api.materials.data.models.AcademicMaterial;
import com.knowlink.api.materials.data.models.AcademicMaterialReport;
import com.knowlink.api.materials.repositories.IAcademicMaterialReportRepository;
import com.knowlink.api.materials.repositories.IAcademicMaterialRepository;
import com.knowlink.api.shared.responses.PagedResponse;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMaterialReportServiceImpl implements IAdminMaterialReportService {

    private static final String TARGET_TYPE = "MATERIAL_REPORT_CASE";
    private static final Sort LIST_SORT = Sort.by(
            Sort.Order.desc("reportsCount"),
            Sort.Order.asc("openedAt"));

    private final IMaterialReportCaseRepository caseRepository;
    private final IAcademicMaterialReportRepository reportRepository;
    private final IAcademicMaterialRepository materialRepository;
    private final IUserRepository userRepository;
    private final IAdminAuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final DateRangeResolver dateRangeResolver;
    private final MaterialReportCaseMapper mapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public MaterialReportCaseListResponse listCases(MaterialReportCaseStatus status, boolean assignedToMe,
            LocalDate from, LocalDate to, Integer page, Integer size, UUID adminUserId) {

        DateRange range = dateRangeResolver.resolve(from, to);
        LocalDateTime fromInclusive = range.fromInclusive();
        LocalDateTime toExclusive = range.toExclusive();
        Pageable pageable = AdminPagination.of(page, size, LIST_SORT);
        UUID assignedAdminId = assignedToMe ? adminUserId : null;

        Page<MaterialReportCase> cases = findCases(
                status, assignedAdminId, fromInclusive, toExclusive, pageable);

        Page<MaterialReportCaseListItemResponse> mapped = cases
                .map(reportCase -> mapper.toListItem(reportCase, adminUserId));

        StatusCountsResponse statusCounts = new StatusCountsResponse(
                countByStatus(MaterialReportCaseStatus.PENDING, assignedAdminId, fromInclusive, toExclusive),
                countByStatus(MaterialReportCaseStatus.IN_REVIEW, assignedAdminId, fromInclusive, toExclusive),
                countByStatus(MaterialReportCaseStatus.REMOVED, assignedAdminId, fromInclusive, toExclusive),
                countByStatus(MaterialReportCaseStatus.DISMISSED, assignedAdminId, fromInclusive, toExclusive));

        return new MaterialReportCaseListResponse(PagedResponse.from(mapped), statusCounts);
    }

    @Override
    @Transactional(readOnly = true)
    public MaterialReportCaseDetailResponse getCaseDetail(UUID caseId, UUID adminUserId) {
        return buildDetail(caseId, adminUserId);
    }

    @Override
    @Transactional
    public MaterialReportCaseDetailResponse assignCase(UUID caseId, UUID adminUserId) {
        User admin = findAdmin(adminUserId);
        LocalDateTime now = LocalDateTime.now(clock);

        int updated = caseRepository.assignIfPending(caseId, admin, now);
        if (updated == 0) {
            MaterialReportCase existing = caseRepository.findById(caseId)
                    .orElseThrow(() -> caseNotFound(caseId));
            if (existing.getStatus() == MaterialReportCaseStatus.IN_REVIEW) {
                throw new BusinessConflictException(
                        "CASE_ALREADY_TAKEN",
                        "Otro administrador ya tomó esta solicitud.",
                        "Material report case " + caseId + " is already in review");
            }
            throw new BusinessConflictException(
                    "CASE_NOT_PENDING",
                    "La solicitud ya no está pendiente.",
                    "Material report case " + caseId + " is not pending: " + existing.getStatus());
        }

        auditService.record(adminUserId, AdminAuditAction.CASE_TAKEN, TARGET_TYPE, caseId);
        log.info("Admin {} took material report case {}", adminUserId, caseId);
        return buildDetail(caseId, adminUserId);
    }

    @Override
    @Transactional
    public MaterialReportCaseDetailResponse removeMaterial(UUID caseId, String reason, UUID adminUserId) {
        String resolutionNote = normalizeNote(reason);
        LocalDateTime now = LocalDateTime.now(clock);
        User admin = findAdmin(adminUserId);

        int updated = caseRepository.resolveIfInReview(
                caseId, admin, MaterialReportCaseStatus.REMOVED, now, resolutionNote);
        if (updated == 0) {
            throw resolutionFailure(caseId, adminUserId);
        }

        MaterialReportCase reportCase = findCaseDetail(caseId);
        List<AcademicMaterialReport> reports = findReports(caseId);
        UUID materialId = reportCase.getAcademicMaterial().getAcademicMaterialId();

        materialRepository.removeFromCatalog(materialId);

        User reviewer = findAdmin(adminUserId);
        reportRepository.resolveReportsByCase(
                reportCase, MaterialReportStatus.RESOLVED, MaterialReportResolution.UPHELD, reviewer);

        publishResolvedEvent(reportCase, reports, MaterialReportCaseStatus.REMOVED, resolutionNote);
        auditService.record(adminUserId, AdminAuditAction.MATERIAL_REMOVED, TARGET_TYPE, caseId);

        log.info("Admin {} removed material {} for material report case {}", adminUserId, materialId, caseId);
        return buildDetail(caseId, adminUserId);
    }

    @Override
    @Transactional
    public MaterialReportCaseDetailResponse dismissCase(UUID caseId, UUID adminUserId) {
        LocalDateTime now = LocalDateTime.now(clock);
        User admin = findAdmin(adminUserId);

        int updated = caseRepository.resolveIfInReview(
                caseId, admin, MaterialReportCaseStatus.DISMISSED, now, null);
        if (updated == 0) {
            throw resolutionFailure(caseId, adminUserId);
        }

        MaterialReportCase reportCase = findCaseDetail(caseId);
        List<AcademicMaterialReport> reports = findReports(caseId);
        UUID materialId = reportCase.getAcademicMaterial().getAcademicMaterialId();

        materialRepository.markAvailable(materialId);

        User reviewer = findAdmin(adminUserId);
        reportRepository.resolveReportsByCase(
                reportCase, MaterialReportStatus.RESOLVED, MaterialReportResolution.DISMISSED, reviewer);

        publishResolvedEvent(reportCase, reports, MaterialReportCaseStatus.DISMISSED, null);
        auditService.record(adminUserId, AdminAuditAction.REPORT_DISMISSED, TARGET_TYPE, caseId);

        log.info("Admin {} dismissed material report case {} for material {}", adminUserId, caseId, materialId);
        return buildDetail(caseId, adminUserId);
    }

    private Page<MaterialReportCase> findCases(MaterialReportCaseStatus status, UUID assignedAdminId,
            LocalDateTime from, LocalDateTime to, Pageable pageable) {
        if (status == null) {
            return assignedAdminId == null
                    ? caseRepository.findCasesAnyStatus(from, to, pageable)
                    : caseRepository.findCasesAnyStatusAssignedTo(assignedAdminId, from, to, pageable);
        }
        return assignedAdminId == null
                ? caseRepository.findCasesByStatus(status, from, to, pageable)
                : caseRepository.findCasesByStatusAssignedTo(status, assignedAdminId, from, to, pageable);
    }

    private long countByStatus(MaterialReportCaseStatus status, UUID assignedAdminId,
            LocalDateTime from, LocalDateTime to) {
        return assignedAdminId == null
                ? caseRepository.countByStatusAndOpenedAt(status, from, to)
                : caseRepository.countByStatusAndOpenedAtAssignedTo(status, assignedAdminId, from, to);
    }

    private MaterialReportCaseDetailResponse buildDetail(UUID caseId, UUID adminUserId) {
        MaterialReportCase reportCase = findCaseDetail(caseId);
        List<AcademicMaterialReport> reports = findReports(caseId);
        return mapper.toDetail(reportCase, reports, adminUserId);
    }

    private MaterialReportCase findCaseDetail(UUID caseId) {
        return caseRepository.findDetailById(caseId)
                .orElseThrow(() -> caseNotFound(caseId));
    }

    private List<AcademicMaterialReport> findReports(UUID caseId) {
        return reportRepository.findByReportCaseIdWithReporter(caseId);
    }

    private User findAdmin(UUID adminUserId) {
        return userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "USER_NOT_FOUND",
                        "El usuario no existe.",
                        "Administrator not found: " + adminUserId));
    }

    private RuntimeException resolutionFailure(UUID caseId, UUID adminUserId) {
        MaterialReportCase existing = caseRepository.findById(caseId)
                .orElseThrow(() -> caseNotFound(caseId));

        return switch (existing.getStatus()) {
            case PENDING -> new BusinessConflictException(
                    "CASE_NOT_IN_REVIEW",
                    "La solicitud todavía no fue tomada.",
                    "Material report case " + caseId + " is still pending");
            case IN_REVIEW -> new NotAssignedToAdminException(
                    "Material report case " + caseId + " is assigned to another administrator, not " + adminUserId);
            case REMOVED, DISMISSED -> new BusinessConflictException(
                    "CASE_ALREADY_RESOLVED",
                    "Esta solicitud ya fue resuelta y no puede modificarse.",
                    "Material report case " + caseId + " is already resolved: " + existing.getStatus());
        };
    }

    private void publishResolvedEvent(MaterialReportCase reportCase, List<AcademicMaterialReport> reports,
            MaterialReportCaseStatus resolution, String removalReason) {
        AcademicMaterial material = reportCase.getAcademicMaterial();
        UUID tutorUserId = material.getTutorSubject().getTutorProfile().getUser().getUserId();
        Set<UUID> reporterUserIds = reports.stream()
                .map(report -> report.getReporter().getUserId())
                .collect(Collectors.toSet());

        eventPublisher.publishEvent(new MaterialReportResolvedEvent(
                reportCase.getMaterialReportCaseId(),
                resolution,
                tutorUserId,
                material.getName(),
                resolution == MaterialReportCaseStatus.REMOVED ? removalReason : null,
                reporterUserIds));
    }

    private String normalizeNote(String reason) {
        if (reason == null) {
            return null;
        }
        String trimmed = reason.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ResourceNotFoundException caseNotFound(UUID caseId) {
        return new ResourceNotFoundException(
                "MATERIAL_REPORT_CASE_NOT_FOUND",
                "La denuncia no existe.",
                "Material report case not found: " + caseId);
    }
}