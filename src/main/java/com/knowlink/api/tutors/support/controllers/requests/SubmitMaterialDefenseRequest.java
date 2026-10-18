package com.knowlink.api.tutors.support.controllers.requests;

import jakarta.validation.constraints.Size;

public record SubmitMaterialDefenseRequest(
        @Size(max = 2000, message = "Description must not exceed {max} characters")
        String description) {

    public SubmitMaterialDefenseRequest {
        if (description != null) {
            description = description.trim();
            if (description.isEmpty()) {
                description = null;
            }
        }
    }
}
