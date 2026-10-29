package com.knowlink.api.admin.dashboard.services.implementations;

import com.knowlink.api.admin.claims.repositories.ISessionClaimMetricsRepository;
import com.knowlink.api.admin.dashboard.DashboardMath;
import com.knowlink.api.admin.dashboard.controllers.responses.AdminNavigationBadgesResponse;
import com.knowlink.api.admin.dashboard.controllers.responses.DashboardSummaryResponse;
import com.knowlink.api.admin.dashboard.controllers.responses.MaterialReportsBlock;
import com.knowlink.api.admin.dashboard.controllers.responses.MostReportedFile;
import com.knowlink.api.admin.dashboard.controllers.responses.PerformanceBlock;
import com.knowlink.api.admin.dashboard.controllers.responses.SessionClaimsBlock;
import com.knowlink.api.admin.dashboard.data.enums.DashboardRange;
import com.knowlink.api.admin.dashboard.services.interfaces.IAdminDashboardService;
import com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus;
import com.knowlink.api.admin.materialreports.data.models.MaterialReportCase;
import com.knowlink.api.admin.materialreports.repositories.IMaterialReportMetricsRepository;
import com.knowlink.api.claims.data.enums.ClaimStatus;
import com.knowlink.api.materials.data.models.AcademicMaterial;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Orchestrates the read ports of the material reports and session claims queues. All the counting
 * and averaging happens in the database; this class only combines the results.
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements IAdminDashboardService {

    private final IMaterialReportMetricsRepository materialReportMetricsRepository;
    private final ISessionClaimMetricsRepository sessionClaimMetricsRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public AdminNavigationBadgesResponse getBadges() {
        return new AdminNavigationBadgesResponse(
                toInt(materialReportMetricsRepository.countPending()),
                toInt(sessionClaimMetricsRepository.countOpen()));
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(DashboardRange range) {
        DashboardRange effectiveRange = range == null ? DashboardRange.WEEK : range;

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime last7 = now.minusDays(7);
        LocalDateTime previous7 = now.minusDays(14);
        LocalDateTime last30 = now.minusDays(30);
        LocalDateTime previous30 = now.minusDays(60);

        return new DashboardSummaryResponse(
                buildSessionClaimsBlock(last7, previous7, now),
                buildMaterialReportsBlock(last7, previous7, now),
                buildPerformanceBlock(effectiveRange, last7, previous7, last30, previous30, now));
    }

    private SessionClaimsBlock buildSessionClaimsBlock(
            LocalDateTime last7, LocalDateTime previous7, LocalDateTime now) {

        long resolved = sessionClaimMetricsRepository.countResolvedBetween(last7, now);
        long previousResolved = sessionClaimMetricsRepository.countResolvedBetween(previous7, last7);
        long newThisWeek = sessionClaimMetricsRepository.countOpenedBetweenWithStatus(
                last7, now, ClaimStatus.OPEN);
        long pending = sessionClaimMetricsRepository.countOpen();

        Integer oldestPendingDays = null;
        if (pending > 0) {
            LocalDateTime oldest = sessionClaimMetricsRepository.findOldestPendingOpenedAt();
            if (oldest != null) {
                oldestPendingDays = toInt(Math.max(0L, Duration.between(oldest, now).toDays()));
            }
        }

        return new SessionClaimsBlock(
                toInt(resolved),
                DashboardMath.percentageDelta(resolved, previousResolved),
                toInt(newThisWeek),
                toInt(pending),
                oldestPendingDays);
    }

    private MaterialReportsBlock buildMaterialReportsBlock(
            LocalDateTime last7, LocalDateTime previous7, LocalDateTime now) {

        long resolved = materialReportMetricsRepository.countResolvedBetween(last7, now);
        long previousResolved = materialReportMetricsRepository.countResolvedBetween(previous7, last7);
        long newThisWeek = materialReportMetricsRepository.countOpenedBetweenWithStatus(
                last7, now, MaterialReportCaseStatus.PENDING);
        long pending = materialReportMetricsRepository.countPending();

        MostReportedFile mostReportedPendingFile = null;
        if (pending > 0) {
            mostReportedPendingFile = materialReportMetricsRepository.findMostReportedPending()
                    .map(this::toMostReportedFile)
                    .orElse(null);
        }

        return new MaterialReportsBlock(
                toInt(resolved),
                DashboardMath.percentageDelta(resolved, previousResolved),
                toInt(newThisWeek),
                toInt(pending),
                mostReportedPendingFile);
    }

    private PerformanceBlock buildPerformanceBlock(
            DashboardRange range,
            LocalDateTime last7,
            LocalDateTime previous7,
            LocalDateTime last30,
            LocalDateTime previous30,
            LocalDateTime now) {

        LocalDateTime currentFrom = range == DashboardRange.WEEK ? last7 : last30;
        LocalDateTime previousFrom = range == DashboardRange.WEEK ? previous7 : previous30;
        LocalDateTime previousTo = range == DashboardRange.WEEK ? last7 : last30;

        long claimsResolved = sessionClaimMetricsRepository.countResolvedBetween(currentFrom, now);
        long reportsResolved = materialReportMetricsRepository.countResolvedBetween(currentFrom, now);
        Double currentAvgHours = averageHours(
                sessionClaimMetricsRepository.avgResolutionSeconds(currentFrom, now),
                claimsResolved,
                materialReportMetricsRepository.avgResolutionSeconds(currentFrom, now),
                reportsResolved);

        long previousClaimsResolved = sessionClaimMetricsRepository.countResolvedBetween(
                previousFrom, previousTo);
        long previousReportsResolved = materialReportMetricsRepository.countResolvedBetween(
                previousFrom, previousTo);
        Double previousAvgHours = averageHours(
                sessionClaimMetricsRepository.avgResolutionSeconds(previousFrom, previousTo),
                previousClaimsResolved,
                materialReportMetricsRepository.avgResolutionSeconds(previousFrom, previousTo),
                previousReportsResolved);

        long openedClaims = sessionClaimMetricsRepository.countOpenedBetween(currentFrom, now);
        long openedReports = materialReportMetricsRepository.countOpenedBetween(currentFrom, now);
        long unresolvedClaims = sessionClaimMetricsRepository.countOpenedBetweenUnresolved(
                currentFrom, now);
        long unresolvedReports = materialReportMetricsRepository.countOpenedBetweenUnresolved(
                currentFrom, now);

        long totalOpened = openedClaims + openedReports;
        long unresolved = unresolvedClaims + unresolvedReports;
        double unresolvedPct = totalOpened == 0
                ? 0.0
                : DashboardMath.round1(unresolved * 100.0 / totalOpened);

        return new PerformanceBlock(
                range,
                currentAvgHours,
                DashboardMath.ttrDeltaPct(previousAvgHours, currentAvgHours),
                unresolvedPct,
                DashboardMath.unresolvedLevel(unresolvedPct),
                DashboardMath.volumeSplit(openedClaims, openedReports));
    }

    /**
     * Weighted average of both queues, because each port returns its own aggregate average.
     */
    private Double averageHours(
            Double claimsAvgSeconds, long claimsResolved,
            Double reportsAvgSeconds, long reportsResolved) {

        double totalSeconds = 0.0;
        long totalResolved = 0L;

        if (claimsAvgSeconds != null && claimsResolved > 0) {
            totalSeconds += claimsAvgSeconds * claimsResolved;
            totalResolved += claimsResolved;
        }
        if (reportsAvgSeconds != null && reportsResolved > 0) {
            totalSeconds += reportsAvgSeconds * reportsResolved;
            totalResolved += reportsResolved;
        }

        if (totalResolved == 0) {
            return null;
        }
        return DashboardMath.round1(totalSeconds / totalResolved / 3600.0);
    }

    private MostReportedFile toMostReportedFile(MaterialReportCase reportCase) {
        AcademicMaterial material = reportCase.getAcademicMaterial();
        return new MostReportedFile(
                reportCase.getMaterialReportCaseId(),
                material.getAcademicMaterialId(),
                fileName(material),
                reportCase.getReportsCount());
    }

    private String fileName(AcademicMaterial material) {
        String originalFileName = material.getOriginalFileName();
        return originalFileName != null && !originalFileName.isBlank()
                ? originalFileName
                : material.getName();
    }

    private int toInt(long value) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(Integer.MIN_VALUE, value));
    }
}
