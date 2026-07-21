package com.knowlink.api.tutors.availability.controllers.requests;

import jakarta.validation.Valid;
import java.util.List;

public record SaveAvailabilityBlocksRequest(
        @Valid
        List<AvailabilityBlockRequest> blocks
) {}