package com.knowlink.api.tutors.availability.validations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import com.knowlink.api.tutors.availability.controllers.requests.AvailabilityBlockRequest;
import java.util.UUID;

public interface IAvailabilityBlockValidationService {
    void validateBlocks(List<AvailabilityBlockRequest> blocks);

    void validateWeekRequest(LocalDate weekStart, LocalDate weekEnd, List<AvailabilityBlockRequest> blocks);

    Set<LocalDate> resolveProtectedDates(
            UUID tutorUserId, UUID tutorProfileId, List<AvailabilityBlockRequest> blocks,
            LocalDate weekStart, LocalDate weekEnd, LocalDateTime now);
}