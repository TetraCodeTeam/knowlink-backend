package com.knowlink.api.tutors.availability.services.interfaces;

import com.knowlink.api.tutors.availability.controllers.requests.AvailabilityBlockRequest;
import com.knowlink.api.tutors.availability.controllers.responses.AvailabilityBlockResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface IAvailabilityBlockService {
    List<AvailabilityBlockResponse> replaceWeekBlocks(
            UUID tutorUserId, LocalDate weekStart, LocalDate weekEnd, List<AvailabilityBlockRequest> blocks);

    List<AvailabilityBlockResponse> getBlocksInRange(UUID tutorUserId, LocalDate from, LocalDate to);

    void removeWeekCustomization(UUID tutorUserId, LocalDate weekStart);

    boolean isWeekCustomized(UUID tutorUserId, LocalDate weekStart);
}