package com.knowlink.api.catalog.controllers.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record CreateSubjectRequest(

        @NotBlank(message = "Subject name is required")
        String name,

        @NotEmpty(message = "At least one career is required")
        List<UUID> careerIds
) {}