package com.knowlink.api.catalog.controllers.requests;

import com.knowlink.api.catalog.data.enums.CareerType;
import jakarta.validation.constraints.NotBlank;

public record CreateCareerRequest(

        @NotBlank(message = "Career name is required")
        String name,

        CareerType type
) {}