package com.knowlink.api.tutors.schedule.controllers.responses;

public record WeeklyScheduleSummaryResponse(
        int confirmedBookingsCount,
        int freeBlocksCount,
        NextClassResponse nextClass
) {}
