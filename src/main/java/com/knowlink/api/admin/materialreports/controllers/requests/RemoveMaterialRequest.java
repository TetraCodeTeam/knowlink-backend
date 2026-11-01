package com.knowlink.api.admin.materialreports.controllers.requests;

import jakarta.validation.constraints.Size;

public record RemoveMaterialRequest(
        @Size(max = 500, message = "Reason must be at most 500 characters") String reason) {

    public RemoveMaterialRequest {
        if (reason != null) {
            reason = reason.trim();
            if (reason.isEmpty()) {
                reason = null;
            }
        }
    }
}