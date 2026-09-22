package com.knowlink.api.tutors.controllers.requests;

import jakarta.validation.constraints.Min;

public record UpdateMinNoticeMinutesRequest(
        @Min(value = 0, message = "Min notice minutes must be zero or greater")
        Integer minNoticeMinutes
) {}