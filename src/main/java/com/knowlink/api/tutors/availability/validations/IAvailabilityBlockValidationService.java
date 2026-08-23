package com.knowlink.api.tutors.availability.validations;
import java.time.LocalDate;
import java.util.List;
import com.knowlink.api.tutors.availability.controllers.requests.AvailabilityBlockRequest;
import java.util.UUID;

public interface IAvailabilityBlockValidationService {
    void validateBlocks(List<AvailabilityBlockRequest> blocks);
    void validateWeekRequest(LocalDate weekStart, LocalDate weekEnd, List<AvailabilityBlockRequest> blocks);
    void validateNoActiveBookingsInRange(UUID tutorUserId, LocalDate from, LocalDate to);
}