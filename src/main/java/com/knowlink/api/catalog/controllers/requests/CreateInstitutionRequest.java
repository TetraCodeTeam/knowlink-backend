package com.knowlink.api.catalog.controllers.requests;

import jakarta.validation.constraints.NotBlank;

public record CreateInstitutionRequest(

        @NotBlank(message = "Institution name is required")
        String name
) {}