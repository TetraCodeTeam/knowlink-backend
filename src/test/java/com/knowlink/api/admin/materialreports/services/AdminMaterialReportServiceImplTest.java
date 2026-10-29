package com.knowlink.api.admin.materialreports.services;

import com.knowlink.api.admin.common.DateRange;
import com.knowlink.api.admin.common.DateRangeResolver;
import com.knowlink.api.admin.common.audit.AdminAuditAction;
import com.knowlink.api.admin.common.audit.IAdminAuditService;
import com.knowlink.api.admin.materialreports.controllers.responses.MaterialReportCaseDetailResponse;
import com.knowlink.api.admin.materialreports.controllers.responses.MaterialReportCaseListResponse;
import com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus;
import com.knowlink.api.admin.materialreports.data.mappers.MaterialReportCaseMapper;
import com.knowlink.api.admin.materialreports.data.models.MaterialReportCase;
import com.knowlink.api.admin.materialreports.events.MaterialReportResolvedEvent;
import com.knowlink.api.admin.materialreports.repositories.IMaterialReportCaseRepository;
import com.knowlink.api.admin.materialreports.services.implementations.AdminMaterialReportServiceImpl;
import com.knowlink.api.exceptions.custom_exceptions.BusinessConflictException;
import com.knowlink.api.exceptions.custom_exceptions.NotAssignedToAdminException;
import com.knowlink.api.materials.data.enums.MaterialReportReason;
import com.knowlink.api.materials.data.enums.MaterialReportResolution;
import com.knowlink.api.materials.data.enums.MaterialReportStatus;
import com.knowlink.api.materials.data.enums.MaterialType;
import com.knowlink.api.materials.data.models.AcademicMaterial;
import com.knowlink.api.materials.data.models.AcademicMaterialReport;
import com.knowlink.api.materials.repositories.IAcademicMaterialReportRepository;
import com.knowlink.api.materials.repositories.IAcademicMaterialRepository;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMaterialReportServiceImplTest {

    @Mock
    private IMaterialReportCaseRepository caseRepository;
    @Mock
    private IAcademicMaterialReportRepository reportRepository;
    @Mock
    private IAcademicMaterialRepository materialRepository;
    @Mock
    private IUserRepository userRepository;
    @Mock
    private IAdminAuditService auditService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private DateRangeResolver dateRangeResolver;

    private final MaterialReportCaseMapper mapper = new MaterialReportCaseMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC);

    private AdminMaterialReportServiceImpl service;
    private User admin;
    private User otherAdmin;
    private User tutor;
    private User reporter;
    private AcademicMaterial material;
    private UUID caseId;

    @BeforeEach
    void setUp() {
        service = new AdminMaterialReportServiceImpl(
                caseRepository, reportRepository, materialRepository, userRepository,
                auditService, eventPublisher, dateRangeResolver, mapper, clock);

        admin = User.builder().userId(UUID.randomUUID()).fullName("Admin One").build();
        otherAdmin = User.builder().userId(UUID.randomUUID()).fullName("Admin Two").build();
        tutor = User.builder().userId(UUID.randomUUID()).fullName("Tutor One").dni("30111222").build();
        reporter = User.builder().userId(UUID.randomUUID()).fullName("Reporter One").build();

        Career career = Career.builder().careerId(UUID.randomUUID()).name("Ingeniería en Sistemas").build();
        TutorProfile tutorProfile = TutorProfile.builder()
                .tutorProfileId(UUID.randomUUID())
                .user(tutor)
                .career(career)
                .institutionalId("LEG-001")
                .build();
        Subject subject = Subject.builder().subjectId(UUID.randomUUID()).name("Matemática").build();
        TutorSubject tutorSubject = TutorSubject.builder()
                .tutorSubjectId(UUID.randomUUID())
                .tutorProfile(tutorProfile)
                .subject(subject)
                .build();

        material = AcademicMaterial.builder()
                .academicMaterialId(UUID.randomUUID())
                .name("Material")
                .originalFileName("material.pdf")
                .storagePath("materials/material.pdf")
                .sizeInBytes(2048L)
                .uploadedAt(LocalDateTime.of(2026, 10, 15, 10, 0))
                .materialType(MaterialType.PDF)
                .tutorSubject(tutorSubject)
                .active(true)
                .available(true)
                .build();

        caseId = UUID.randomUUID();
    }

    @Test
    void listFiltersByStatusAndComputesCountsIgnoringStatus() {
        DateRange range = new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 28));
        when(dateRangeResolver.resolve(isNull(), isNull())).thenReturn(range);

        MaterialReportCase pendingCase = reportCase(MaterialReportCaseStatus.PENDING, null, null, 4);
        Page<MaterialReportCase> page = new PageImpl<>(List.of(pendingCase));

        when(caseRepository.findCasesByStatusAssignedTo(
                eq(MaterialReportCaseStatus.PENDING), eq(admin.getUserId()),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(page);
        when(caseRepository.countByStatusAndOpenedAtAssignedTo(
                eq(MaterialReportCaseStatus.PENDING), eq(admin.getUserId()),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(3L);
        when(caseRepository.countByStatusAndOpenedAtAssignedTo(
                eq(MaterialReportCaseStatus.IN_REVIEW), eq(admin.getUserId()),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(2L);
        when(caseRepository.countByStatusAndOpenedAtAssignedTo(
                eq(MaterialReportCaseStatus.REMOVED), eq(admin.getUserId()),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1L);
        when(caseRepository.countByStatusAndOpenedAtAssignedTo(
                eq(MaterialReportCaseStatus.DISMISSED), eq(admin.getUserId()),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(0L);

        MaterialReportCaseListResponse response = service.listCases(
                MaterialReportCaseStatus.PENDING, true, null, null, 0, 10, admin.getUserId());

        assertThat(response.page().content()).hasSize(1);
        assertThat(response.page().content().get(0).reportsCount()).isEqualTo(4);
        assertThat(response.page().content().get(0).fileName()).isEqualTo("material.pdf");
        assertThat(response.statusCounts().pending()).isEqualTo(3L);
        assertThat(response.statusCounts().inReview()).isEqualTo(2L);
        assertThat(response.statusCounts().removed()).isEqualTo(1L);
        assertThat(response.statusCounts().dismissed()).isZero();
    }

    @Test
    void assignTakesPendingCaseAndAudits() {
        when(userRepository.findById(admin.getUserId())).thenReturn(Optional.of(admin));
        when(caseRepository.assignIfPending(eq(caseId), eq(admin), any(LocalDateTime.class))).thenReturn(1);

        MaterialReportCase inReview = reportCase(MaterialReportCaseStatus.IN_REVIEW, admin, null, 2);
        when(caseRepository.findDetailById(caseId)).thenReturn(Optional.of(inReview));
        when(reportRepository.findByReportCaseIdWithReporter(caseId)).thenReturn(List.of());

        MaterialReportCaseDetailResponse response = service.assignCase(caseId, admin.getUserId());

        assertThat(response.summary().status()).isEqualTo(MaterialReportCaseStatus.IN_REVIEW);
        assertThat(response.summary().assignedToMe()).isTrue();
        assertThat(response.permissions().canResolve()).isTrue();
        assertThat(response.permissions().canTake()).isFalse();
        verify(auditService).record(
                admin.getUserId(), AdminAuditAction.CASE_TAKEN, "MATERIAL_REPORT_CASE", caseId);
    }

    @Test
    void assignFailsWhenAnotherAdminAlreadyTookTheCase() {
        when(userRepository.findById(admin.getUserId())).thenReturn(Optional.of(admin));
        when(caseRepository.assignIfPending(eq(caseId), eq(admin), any(LocalDateTime.class))).thenReturn(0);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(
                reportCase(MaterialReportCaseStatus.IN_REVIEW, otherAdmin, null, 1)));

        assertThatThrownBy(() -> service.assignCase(caseId, admin.getUserId()))
                .isInstanceOf(BusinessConflictException.class)
                .satisfies(exception -> assertThat(((BusinessConflictException) exception).getErrorCode())
                        .isEqualTo("CASE_ALREADY_TAKEN"));

        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    void removeMaterialResolvesReportsPublishesEventAndAudits() {
        when(userRepository.findById(admin.getUserId())).thenReturn(Optional.of(admin));
        when(caseRepository.resolveIfInReview(
                eq(caseId), eq(admin), eq(MaterialReportCaseStatus.REMOVED),
                any(LocalDateTime.class), eq("Contenido inapropiado"))).thenReturn(1);

        MaterialReportCase removed = reportCase(
                MaterialReportCaseStatus.REMOVED, admin, "Contenido inapropiado", 2);
        AcademicMaterialReport report = report();
        when(caseRepository.findDetailById(caseId)).thenReturn(Optional.of(removed));
        when(reportRepository.findByReportCaseIdWithReporter(caseId)).thenReturn(List.of(report));
        when(materialRepository.removeFromCatalog(material.getAcademicMaterialId())).thenReturn(1);
        when(reportRepository.resolveReportsByCase(
                eq(removed), eq(MaterialReportStatus.RESOLVED),
                eq(MaterialReportResolution.UPHELD), any(User.class))).thenReturn(1);

        MaterialReportCaseDetailResponse response = service.removeMaterial(
                caseId, "  Contenido inapropiado  ", admin.getUserId());

        ArgumentCaptor<MaterialReportResolvedEvent> eventCaptor =
                ArgumentCaptor.forClass(MaterialReportResolvedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        MaterialReportResolvedEvent event = eventCaptor.getValue();

        assertThat(event.resolution()).isEqualTo(MaterialReportCaseStatus.REMOVED);
        assertThat(event.removalReason()).isEqualTo("Contenido inapropiado");
        assertThat(event.tutorUserId()).isEqualTo(tutor.getUserId());
        assertThat(event.reporterUserIds()).containsExactly(reporter.getUserId());
        assertThat(response.summary().status()).isEqualTo(MaterialReportCaseStatus.REMOVED);
        assertThat(response.summary().resolutionNote()).isEqualTo("Contenido inapropiado");
        verify(auditService).record(
                admin.getUserId(), AdminAuditAction.MATERIAL_REMOVED, "MATERIAL_REPORT_CASE", caseId);
    }

    @Test
    void dismissKeepsMaterialAndResolvesReportsAsDismissed() {
        when(userRepository.findById(admin.getUserId())).thenReturn(Optional.of(admin));
        when(caseRepository.resolveIfInReview(
                eq(caseId), eq(admin), eq(MaterialReportCaseStatus.DISMISSED),
                any(LocalDateTime.class), isNull())).thenReturn(1);

        MaterialReportCase dismissed = reportCase(
                MaterialReportCaseStatus.DISMISSED, admin, null, 1);
        when(caseRepository.findDetailById(caseId)).thenReturn(Optional.of(dismissed));
        when(reportRepository.findByReportCaseIdWithReporter(caseId)).thenReturn(List.of(report()));
        when(materialRepository.markAvailable(material.getAcademicMaterialId())).thenReturn(1);
        when(reportRepository.resolveReportsByCase(
                eq(dismissed), eq(MaterialReportStatus.RESOLVED),
                eq(MaterialReportResolution.DISMISSED), any(User.class))).thenReturn(1);

        MaterialReportCaseDetailResponse response = service.dismissCase(caseId, admin.getUserId());

        ArgumentCaptor<MaterialReportResolvedEvent> eventCaptor =
                ArgumentCaptor.forClass(MaterialReportResolvedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        MaterialReportResolvedEvent event = eventCaptor.getValue();

        assertThat(event.resolution()).isEqualTo(MaterialReportCaseStatus.DISMISSED);
        assertThat(event.removalReason()).isNull();
        assertThat(response.summary().status()).isEqualTo(MaterialReportCaseStatus.DISMISSED);
        assertThat(response.summary().resolutionNote()).isNull();
        verify(materialRepository).markAvailable(material.getAcademicMaterialId());
        verify(auditService).record(
                admin.getUserId(), AdminAuditAction.REPORT_DISMISSED, "MATERIAL_REPORT_CASE", caseId);
    }

    @Test
    void resolvingCaseAssignedToAnotherAdminIsForbidden() {
        when(userRepository.findById(admin.getUserId())).thenReturn(Optional.of(admin));
        when(caseRepository.resolveIfInReview(
                eq(caseId), eq(admin), eq(MaterialReportCaseStatus.DISMISSED),
                any(LocalDateTime.class), isNull())).thenReturn(0);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(
                reportCase(MaterialReportCaseStatus.IN_REVIEW, otherAdmin, null, 1)));

        assertThatThrownBy(() -> service.dismissCase(caseId, admin.getUserId()))
                .isInstanceOf(NotAssignedToAdminException.class);

        verify(eventPublisher, never()).publishEvent(any(MaterialReportResolvedEvent.class));
    }

    @Test
    void detailExposesReadOnlyPermissionsForCasesAssignedToAnotherAdmin() {
        when(caseRepository.findDetailById(caseId)).thenReturn(Optional.of(
                reportCase(MaterialReportCaseStatus.IN_REVIEW, otherAdmin, null, 1)));
        when(reportRepository.findByReportCaseIdWithReporter(caseId)).thenReturn(List.of());

        MaterialReportCaseDetailResponse response = service.getCaseDetail(caseId, admin.getUserId());

        assertThat(response.permissions().canTake()).isFalse();
        assertThat(response.permissions().canResolve()).isFalse();
        assertThat(response.tutor().dni()).isEqualTo("30111222");
        assertThat(response.tutor().institutionalId()).isEqualTo("LEG-001");
        assertThat(response.reports()).isEmpty();
    }

    @Test
    void detailAllowsTakingPendingCase() {
        when(caseRepository.findDetailById(caseId)).thenReturn(Optional.of(
                reportCase(MaterialReportCaseStatus.PENDING, null, null, 3)));
        when(reportRepository.findByReportCaseIdWithReporter(caseId)).thenReturn(List.of());

        MaterialReportCaseDetailResponse response = service.getCaseDetail(caseId, admin.getUserId());

        assertThat(response.permissions().canTake()).isTrue();
        assertThat(response.permissions().canResolve()).isFalse();
    }

    @Test
    void listWithoutAssignedToMeUsesGlobalQueries() {
        DateRange range = new DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 28));
        when(dateRangeResolver.resolve(isNull(), isNull())).thenReturn(range);
        when(caseRepository.findCasesAnyStatus(
                any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        MaterialReportCaseListResponse response = service.listCases(
                null, false, null, null, null, null, admin.getUserId());

        assertThat(response.page().content()).isEmpty();
        assertThat(response.statusCounts().pending()).isZero();
        verify(caseRepository).findCasesAnyStatus(
                any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class));
        verify(caseRepository).countByStatusAndOpenedAt(
                eq(MaterialReportCaseStatus.PENDING), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(caseRepository, never()).findCasesAnyStatusAssignedTo(any(), any(), any(), any());
    }

    @Test
    void assignOnResolvedCaseReturnsCaseNotPending() {
        when(userRepository.findById(admin.getUserId())).thenReturn(Optional.of(admin));
        when(caseRepository.assignIfPending(eq(caseId), eq(admin), any(LocalDateTime.class))).thenReturn(0);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(
                reportCase(MaterialReportCaseStatus.REMOVED, admin, "Contenido inapropiado", 1)));

        assertThatThrownBy(() -> service.assignCase(caseId, admin.getUserId()))
                .isInstanceOf(BusinessConflictException.class)
                .satisfies(exception -> assertThat(((BusinessConflictException) exception).getErrorCode())
                        .isEqualTo("CASE_NOT_PENDING"));

        verify(auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    void resolveOnPendingCaseReturnsCaseNotInReview() {
        when(userRepository.findById(admin.getUserId())).thenReturn(Optional.of(admin));
        when(caseRepository.resolveIfInReview(
                eq(caseId), eq(admin), eq(MaterialReportCaseStatus.REMOVED),
                any(LocalDateTime.class), isNull())).thenReturn(0);
        when(caseRepository.findById(caseId)).thenReturn(Optional.of(
                reportCase(MaterialReportCaseStatus.PENDING, null, null, 1)));

        assertThatThrownBy(() -> service.removeMaterial(caseId, null, admin.getUserId()))
                .isInstanceOf(BusinessConflictException.class)
                .satisfies(exception -> assertThat(((BusinessConflictException) exception).getErrorCode())
                        .isEqualTo("CASE_NOT_IN_REVIEW"));

        verify(materialRepository, never()).removeFromCatalog(any());
    }

    private MaterialReportCase reportCase(MaterialReportCaseStatus status, User assignedAdmin,
            String resolutionNote, int reportsCount) {
        return MaterialReportCase.builder()
                .materialReportCaseId(caseId)
                .academicMaterial(material)
                .status(status)
                .assignedAdmin(assignedAdmin)
                .assignedAt(assignedAdmin == null ? null : LocalDateTime.of(2026, 10, 20, 10, 0))
                .openedAt(LocalDateTime.of(2026, 10, 18, 9, 0))
                .resolvedAt(status.isClosed() ? LocalDateTime.of(2026, 10, 21, 11, 0) : null)
                .resolutionNote(resolutionNote)
                .reportsCount(reportsCount)
                .build();
    }

    private AcademicMaterialReport report() {
        return AcademicMaterialReport.builder()
                .academicMaterialReportId(UUID.randomUUID())
                .material(material)
                .reporter(reporter)
                .reason(MaterialReportReason.PLAGIARISM)
                .description("Copied content")
                .reportedAt(LocalDateTime.of(2026, 10, 18, 9, 30))
                .build();
    }
}