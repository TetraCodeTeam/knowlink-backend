package com.knowlink.api.admin.dashboard;

import com.knowlink.api.admin.claims.repositories.ISessionClaimMetricsRepository;
import com.knowlink.api.admin.dashboard.controllers.responses.DashboardSummaryResponse;
import com.knowlink.api.admin.dashboard.controllers.responses.VolumeSplit;
import com.knowlink.api.admin.dashboard.data.enums.DashboardRange;
import com.knowlink.api.admin.dashboard.data.enums.UnresolvedLevel;
import com.knowlink.api.admin.dashboard.services.implementations.AdminDashboardServiceImpl;
import com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus;
import com.knowlink.api.admin.materialreports.data.models.MaterialReportCase;
import com.knowlink.api.admin.materialreports.repositories.IMaterialReportMetricsRepository;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.materials.data.models.AcademicMaterial;
import com.knowlink.api.shared.utils.AppTimeZone;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminDashboardServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-28T12:00:00Z"), AppTimeZone.ZONE);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final LocalDateTime LAST_7 = NOW.minusDays(7);
    private static final LocalDateTime PREVIOUS_7 = NOW.minusDays(14);
    private static final LocalDateTime LAST_30 = NOW.minusDays(30);
    private static final LocalDateTime PREVIOUS_30 = NOW.minusDays(60);

    @Mock
    private IMaterialReportMetricsRepository materialReportMetricsRepository;

    @Mock
    private ISessionClaimMetricsRepository sessionClaimMetricsRepository;

    private AdminDashboardServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminDashboardServiceImpl(
                materialReportMetricsRepository, sessionClaimMetricsRepository, CLOCK);
    }

    @Test
    @DisplayName("Badges: cuenta todas las denuncias PENDING y los reclamos OPEN")
    void getBadges_returnsPendingCounts() {
        when(materialReportMetricsRepository.countPending()).thenReturn(3L);
        when(sessionClaimMetricsRepository.countOpen()).thenReturn(2L);

        var response = service.getBadges();

        assertThat(response.pendingMaterialReports()).isEqualTo(3);
        assertThat(response.pendingSessionClaims()).isEqualTo(2);
    }

    @Test
    @DisplayName("Resumen: sin pendientes devuelve ceros y nulos, sin consultar el más reportado")
    void getSummary_withoutPendingCases_returnsEmptyBlocks() {
        DashboardSummaryResponse response = service.getSummary(DashboardRange.WEEK);

        assertThat(response.sessionClaims().pendingCount()).isZero();
        assertThat(response.sessionClaims().oldestPendingDays()).isNull();
        assertThat(response.sessionClaims().resolvedDeltaPct()).isNull();
        assertThat(response.materialReports().pendingCount()).isZero();
        assertThat(response.materialReports().mostReportedPendingFile()).isNull();
        assertThat(response.materialReports().resolvedDeltaPct()).isNull();
        assertThat(response.performance().avgResolutionHours()).isNull();
        assertThat(response.performance().avgResolutionDeltaPct()).isNull();
        assertThat(response.performance().unresolvedPct()).isZero();
        assertThat(response.performance().unresolvedLevel()).isEqualTo(UnresolvedLevel.UNDER_CONTROL);
        assertThat(response.performance().volumeSplit()).isEqualTo(new VolumeSplit(0, 0));

        verify(sessionClaimMetricsRepository, never()).findOldestPendingOpenedAt();
        verify(materialReportMetricsRepository, never()).sumPendingReports();
        verify(materialReportMetricsRepository, never()).findMostReportedPending();
    }

    @Test
    @DisplayName("Resumen: incluye pendientes, antigüedad, variación y archivo más reportado")
    void getSummary_withPendingCases_returnsWeeklyBlocks() {
        when(sessionClaimMetricsRepository.countResolvedBetween(LAST_7, NOW)).thenReturn(3L);
        when(sessionClaimMetricsRepository.countResolvedBetween(PREVIOUS_7, LAST_7)).thenReturn(2L);
        when(sessionClaimMetricsRepository.countOpenedBetweenWithStatus(LAST_7, NOW, ClaimStatus.OPEN))
                .thenReturn(1L);
        when(sessionClaimMetricsRepository.countOpen()).thenReturn(2L);
        when(sessionClaimMetricsRepository.findOldestPendingOpenedAt()).thenReturn(NOW.minusDays(45));

        UUID caseId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        MaterialReportCase pendingCase = MaterialReportCase.builder()
                .materialReportCaseId(caseId)
                .academicMaterial(AcademicMaterial.builder()
                        .academicMaterialId(materialId)
                        .name("Guía de derivadas")
                        .originalFileName("derivadas.pdf")
                        .build())
                .reportsCount(6)
                .build();
        when(materialReportMetricsRepository.countResolvedBetween(LAST_7, NOW)).thenReturn(2L);
        when(materialReportMetricsRepository.countResolvedBetween(PREVIOUS_7, LAST_7)).thenReturn(0L);
        when(materialReportMetricsRepository.countOpenedBetweenWithStatus(
                LAST_7, NOW, MaterialReportCaseStatus.PENDING)).thenReturn(2L);
        when(materialReportMetricsRepository.countPending()).thenReturn(4L);
        when(materialReportMetricsRepository.sumPendingReports()).thenReturn(11L);
        when(materialReportMetricsRepository.findMostReportedPending()).thenReturn(Optional.of(pendingCase));

        DashboardSummaryResponse response = service.getSummary(DashboardRange.WEEK);

        assertThat(response.sessionClaims().resolvedThisWeek()).isEqualTo(3);
        assertThat(response.sessionClaims().resolvedDeltaPct()).isEqualTo(50.0);
        assertThat(response.sessionClaims().newThisWeek()).isEqualTo(1);
        assertThat(response.sessionClaims().pendingCount()).isEqualTo(2);
        assertThat(response.sessionClaims().oldestPendingDays()).isEqualTo(45);

        assertThat(response.materialReports().resolvedThisWeek()).isEqualTo(2);
        assertThat(response.materialReports().resolvedDeltaPct()).isNull();
        assertThat(response.materialReports().newThisWeek()).isEqualTo(2);
        assertThat(response.materialReports().pendingCount()).isEqualTo(4);
        assertThat(response.materialReports().pendingReports()).isEqualTo(11);
        assertThat(response.materialReports().mostReportedPendingFile().caseId()).isEqualTo(caseId);
        assertThat(response.materialReports().mostReportedPendingFile().materialId()).isEqualTo(materialId);
        assertThat(response.materialReports().mostReportedPendingFile().fileName()).isEqualTo("derivadas.pdf");
        assertThat(response.materialReports().mostReportedPendingFile().reportsCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("Resumen: range MONTH calcula el rendimiento sobre los últimos 30 días")
    void getSummary_monthRange_usesLast30Days() {
        when(sessionClaimMetricsRepository.countResolvedBetween(LAST_30, NOW)).thenReturn(10L);
        when(sessionClaimMetricsRepository.countResolvedBetween(PREVIOUS_30, LAST_30)).thenReturn(8L);
        when(sessionClaimMetricsRepository.avgResolutionSeconds(LAST_30, NOW)).thenReturn(36_000.0);
        when(sessionClaimMetricsRepository.avgResolutionSeconds(PREVIOUS_30, LAST_30)).thenReturn(72_000.0);
        when(sessionClaimMetricsRepository.countOpenedBetween(LAST_30, NOW)).thenReturn(3L);
        when(sessionClaimMetricsRepository.countOpenedBetweenUnresolved(LAST_30, NOW)).thenReturn(1L);
        when(materialReportMetricsRepository.countOpenedBetween(LAST_30, NOW)).thenReturn(1L);
        when(materialReportMetricsRepository.countOpenedBetweenUnresolved(LAST_30, NOW)).thenReturn(0L);

        DashboardSummaryResponse response = service.getSummary(DashboardRange.MONTH);

        assertThat(response.performance().range()).isEqualTo(DashboardRange.MONTH);
        assertThat(response.performance().avgResolutionHours()).isEqualTo(10.0);
        assertThat(response.performance().avgResolutionDeltaPct()).isEqualTo(50.0);
        assertThat(response.performance().unresolvedPct()).isEqualTo(25.0);
        assertThat(response.performance().unresolvedLevel()).isEqualTo(UnresolvedLevel.ACCEPTABLE);
        assertThat(response.performance().volumeSplit()).isEqualTo(new VolumeSplit(75, 25));

        verify(sessionClaimMetricsRepository).countResolvedBetween(LAST_30, NOW);
        verify(sessionClaimMetricsRepository).countResolvedBetween(PREVIOUS_30, LAST_30);
        verify(materialReportMetricsRepository).countResolvedBetween(LAST_30, NOW);
        verify(materialReportMetricsRepository).countResolvedBetween(PREVIOUS_30, LAST_30);
    }

    @Test
    @DisplayName("Resumen: range WEEK usa las ventanas de 7 días y promedia ambos tipos ponderando por casos")
    void getSummary_weekRange_combinesBothQueues() {
        when(sessionClaimMetricsRepository.countResolvedBetween(LAST_7, NOW)).thenReturn(1L);
        when(sessionClaimMetricsRepository.avgResolutionSeconds(LAST_7, NOW)).thenReturn(7_200.0);
        when(materialReportMetricsRepository.countResolvedBetween(LAST_7, NOW)).thenReturn(3L);
        when(materialReportMetricsRepository.avgResolutionSeconds(LAST_7, NOW)).thenReturn(21_600.0);
        when(sessionClaimMetricsRepository.countResolvedBetween(PREVIOUS_7, LAST_7)).thenReturn(1L);
        when(sessionClaimMetricsRepository.avgResolutionSeconds(PREVIOUS_7, LAST_7)).thenReturn(14_400.0);
        when(materialReportMetricsRepository.countResolvedBetween(PREVIOUS_7, LAST_7)).thenReturn(3L);
        when(materialReportMetricsRepository.avgResolutionSeconds(PREVIOUS_7, LAST_7)).thenReturn(14_400.0);

        DashboardSummaryResponse response = service.getSummary(DashboardRange.WEEK);

        // (7200*1 + 21600*3) / 4 = 18000 s = 5 h
        assertThat(response.performance().avgResolutionHours()).isEqualTo(5.0);
        // previous average 4 h, current 5 h -> 25% slower
        assertThat(response.performance().avgResolutionDeltaPct()).isEqualTo(-25.0);
    }
}
