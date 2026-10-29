package com.knowlink.api.community.jobs;

import com.knowlink.api.community.services.interfaces.ITutorRankingSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class TutorRankingJob {

    private final ITutorRankingSnapshotService snapshotService;
    private final Clock clock;

    /** El ranking se actualiza solo el primer día de cada mes; no hay forma de modificarlo a mano. */
    @Scheduled(cron = "0 5 0 1 * *", zone = "America/Argentina/Cordoba")
    public void generateMonthlyRanking() {
        LocalDate period = LocalDate.now(clock).withDayOfMonth(1);
        try {
            snapshotService.generateIfAbsent(period);
        } catch (RuntimeException ex) {
            log.error("Failed to generate tutor ranking for period {}: {}", period, ex.getMessage(), ex);
        }
    }
}
