package com.knowlink.api.tutors.availability.controllers.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaveAvailabilityBlocksRequest(
        @NotNull(message = "blocks is required")
        @Valid
        List<AvailabilityBlockRequest> blocks
) {}