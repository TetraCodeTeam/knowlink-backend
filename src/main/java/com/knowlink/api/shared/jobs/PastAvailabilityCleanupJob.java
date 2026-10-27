package com.knowlink.api.shared.jobs;

import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.timeslot.repositories.ITimeSlotRepository;
import com.knowlink.api.tutors.availability.repositories.IAvailabilityBlockRepository;
import com.knowlink.api.tutors.statistics.data.models.OfferedSlotHistory;
import com.knowlink.api.tutors.statistics.repositories.IOfferedSlotHistoryRepository;
import com.knowlink.api.tutors.statistics.utils.TutorStatisticsConstants;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PastAvailabilityCleanupJob {

    private final ITimeSlotRepository timeSlotRepository;
    private final IAvailabilityBlockRepository availabilityBlockRepository;
    private final IOfferedSlotHistoryRepository offeredSlotHistoryRepository;

    @Scheduled(cron = "0 0 3 * * *") // una vez al día, sin urgencia de tiempo real
    @Transactional
    public void cleanupPastAvailability() {
        LocalDateTime now = LocalDateTime.now(AppTimeZone.ZONE);

        // Los horarios pasados sin reservas se archivan antes de borrarse: son el denominador
        // ("horas ofrecidas") de la ocupación en el panel de estadísticas del tutor.
        List<TimeSlot> pastUnbooked = timeSlotRepository.findPastUnbooked(now.toLocalDate(), now.toLocalTime());
        offeredSlotHistoryRepository.saveAll(pastUnbooked.stream()
                .map(slot -> OfferedSlotHistory.builder()
                        .tutorProfileId(slot.getTutorProfileId())
                        .date(slot.getDate())
                        .startTime(slot.getStartTime())
                        .endTime(slot.getEndTime())
                        .build())
                .toList());

        timeSlotRepository.deletePastUnbooked(now.toLocalDate(), now.toLocalTime());
        availabilityBlockRepository.deletePastOrphaned(now.toLocalDate(), now.toLocalTime());

        offeredSlotHistoryRepository.deleteOlderThan(
                now.toLocalDate().minusYears(TutorStatisticsConstants.MAX_LOOKBACK_YEARS));
    }
}
