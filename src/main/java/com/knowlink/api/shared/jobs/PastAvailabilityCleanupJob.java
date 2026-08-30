package com.knowlink.api.shared.jobs;

import com.knowlink.api.timeslot.repositories.ITimeSlotRepository;
import com.knowlink.api.tutors.availability.repositories.IAvailabilityBlockRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PastAvailabilityCleanupJob {

    private final ITimeSlotRepository timeSlotRepository;
    private final IAvailabilityBlockRepository availabilityBlockRepository;

    @Scheduled(cron = "0 0 3 * * *") // una vez al día, sin urgencia de tiempo real
    @Transactional
    public void cleanupPastAvailability() {
        LocalDateTime now = LocalDateTime.now();
        timeSlotRepository.deletePastUnbooked(now.toLocalDate(), now.toLocalTime());
        availabilityBlockRepository.deletePastOrphaned(now.toLocalDate(), now.toLocalTime());
    }
}