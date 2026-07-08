package com.knowlink.api.tutors.controllers.responses;

import java.time.LocalTime;

public record TutorAvailabilityResponse(
        String dayOfWeek,
        LocalTime startTime,
        LocalTime endTime
) {}