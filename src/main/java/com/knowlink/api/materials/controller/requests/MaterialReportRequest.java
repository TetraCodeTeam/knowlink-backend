package com.knowlink.api.materials.controller.requests;

import com.knowlink.api.materials.data.enums.MaterialReportReason;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MaterialReportRequest(
        @NotNull(message = "Reason is required")
        MaterialReportReason reason,
        @Size(max = 2000, message = "Description must not exceed {max} characters")
        String description) {

    public MaterialReportRequest {
        if (description != null) {
            description = description.trim();
            if (description.isEmpty()) {
                description = null;
            }
        }
    }
}