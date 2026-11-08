package com.knowlink.api.tutors.controllers.responses;

import java.util.UUID;

public record InstitutionResponse(
        UUID institutionId,
        String name
) {
}
