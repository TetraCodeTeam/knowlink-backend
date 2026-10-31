package com.knowlink.api.admin.dashboard;

import com.knowlink.api.admin.claims.repositories.ISessionClaimMetricsRepository;
import com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus;
import com.knowlink.api.admin.materialreports.repositories.IMaterialReportMetricsRepository;
import com.knowlink.api.claims.data.enums.ClaimStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@ActiveProfiles("test")
class AdminDashboardMetricsRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 12, 0);
    private static final LocalDateTime LAST_7 = NOW.minusDays(7);

    @Autowired
    private IMaterialReportMetricsRepository materialReportMetricsRepository;

    @Autowired
    private ISessionClaimMetricsRepository sessionClaimMetricsRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // The metrics only read the case tables; their parent rows are irrelevant for these tests.
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        jdbcTemplate.update("DELETE FROM material_report_case");
        jdbcTemplate.update("DELETE FROM session_claim");
    }

    @Test
    @DisplayName("Denuncias: conteos por estado y ventana, y promedio de resolución")
    void materialReports_aggregateByStatusAndWindow() {
        insertMaterialCase(MaterialReportCaseStatus.PENDING, NOW.minusDays(3), null, 2);
        insertMaterialCase(MaterialReportCaseStatus.PENDING, NOW.minusDays(10), null, 5);
        insertMaterialCase(MaterialReportCaseStatus.REMOVED, NOW.minusDays(2), NOW.minusHours(5), 1);
        insertMaterialCase(MaterialReportCaseStatus.DISMISSED, NOW.minusDays(10), NOW.minusDays(8), 1);

        assertThat(materialReportMetricsRepository.countPending()).isEqualTo(2);
        // Pending cases carry 2 and 5 reports each.
        assertThat(materialReportMetricsRepository.sumPendingReports()).isEqualTo(7);
        assertThat(materialReportMetricsRepository.countResolvedBetween(LAST_7, NOW)).isEqualTo(1);
        assertThat(materialReportMetricsRepository.countOpenedBetweenWithStatus(
                LAST_7, NOW, MaterialReportCaseStatus.PENDING)).isEqualTo(1);
        assertThat(materialReportMetricsRepository.countOpenedBetween(LAST_7, NOW)).isEqualTo(2);
        assertThat(materialReportMetricsRepository.countOpenedBetweenUnresolved(LAST_7, NOW)).isEqualTo(1);

        // Opened 2 days ago, resolved 5 hours ago: 43 hours.
        assertThat(materialReportMetricsRepository.avgResolutionSeconds(LAST_7, NOW))
                .isCloseTo(43 * 3600.0, within(1.0));
    }

    @Test
    @DisplayName("Denuncias: los bordes de la ventana son [from, to)")
    void materialReports_windowBoundaries() {
        insertMaterialCase(MaterialReportCaseStatus.PENDING, LAST_7, null, 1);
        insertMaterialCase(MaterialReportCaseStatus.PENDING, LAST_7.minusSeconds(1), null, 1);
        insertMaterialCase(MaterialReportCaseStatus.REMOVED, NOW.minusDays(20), LAST_7, 1);
        insertMaterialCase(MaterialReportCaseStatus.REMOVED, NOW.minusDays(20), LAST_7.minusSeconds(1), 1);

        assertThat(materialReportMetricsRepository.countOpenedBetween(LAST_7, NOW)).isEqualTo(1);
        assertThat(materialReportMetricsRepository.countResolvedBetween(LAST_7, NOW)).isEqualTo(1);
    }

    @Test
    @DisplayName("Reclamos: solo cuenta el representante de cada disputa y respeta las ventanas")
    void sessionClaims_groupDisputesAndAggregateByWindow() {
        UUID openDispute = UUID.randomUUID();
        insertSessionClaim(ClaimStatus.OPEN, NOW.minusDays(1), null, openDispute, openDispute);
        insertSessionClaim(ClaimStatus.OPEN, NOW.minusDays(1), null, UUID.randomUUID(), openDispute);

        UUID oldestDispute = UUID.randomUUID();
        insertSessionClaim(ClaimStatus.OPEN, NOW.minusDays(45), null, oldestDispute, oldestDispute);

        UUID resolvedDispute = UUID.randomUUID();
        insertSessionClaim(ClaimStatus.RESOLVED, NOW.minusDays(3), NOW.minusDays(1), resolvedDispute, resolvedDispute);

        UUID previousDispute = UUID.randomUUID();
        insertSessionClaim(ClaimStatus.RESOLVED, NOW.minusDays(10), NOW.minusDays(9), previousDispute, previousDispute);

        assertThat(sessionClaimMetricsRepository.countOpen()).isEqualTo(2);
        assertThat(sessionClaimMetricsRepository.findOldestPendingOpenedAt())
                .isEqualTo(NOW.minusDays(45));
        assertThat(sessionClaimMetricsRepository.countResolvedBetween(LAST_7, NOW)).isEqualTo(1);
        assertThat(sessionClaimMetricsRepository.countOpenedBetweenWithStatus(
                LAST_7, NOW, ClaimStatus.OPEN)).isEqualTo(1);
        assertThat(sessionClaimMetricsRepository.countOpenedBetween(LAST_7, NOW)).isEqualTo(2);
        assertThat(sessionClaimMetricsRepository.countOpenedBetweenUnresolved(LAST_7, NOW)).isEqualTo(1);

        // Opened 3 days ago, resolved 1 day ago: 2 days.
        assertThat(sessionClaimMetricsRepository.avgResolutionSeconds(LAST_7, NOW))
                .isCloseTo(2 * 24 * 3600.0, within(1.0));
    }

    private void insertMaterialCase(
            MaterialReportCaseStatus status, LocalDateTime openedAt, LocalDateTime resolvedAt, int reports) {
        jdbcTemplate.update("""
                INSERT INTO material_report_case
                    (material_report_case_id, academic_material_id, status, opened_at, resolved_at,
                     reports_count, version)
                VALUES (?, ?, ?, ?, ?, ?, 0)
                """,
                UUID.randomUUID(), UUID.randomUUID(), status.name(), openedAt, resolvedAt, reports);
    }

    private void insertSessionClaim(
            ClaimStatus status,
            LocalDateTime createdAt,
            LocalDateTime resolvedAt,
            UUID claimId,
            UUID disputeId) {
        jdbcTemplate.update("""
                INSERT INTO session_claim
                    (claim_id, booking_id, user_id, claimant_role, reason, status, active_key,
                     dispute_id, created_at, updated_at, version, resolved_at)
                VALUES (?, ?, ?, 'STUDENT', 'TUTOR_COULD_NOT_ATTEND', ?, NULL, ?, ?, ?, 0, ?)
                """,
                claimId, UUID.randomUUID(), UUID.randomUUID(), status.name(),
                disputeId, createdAt, createdAt, resolvedAt);
    }
}
