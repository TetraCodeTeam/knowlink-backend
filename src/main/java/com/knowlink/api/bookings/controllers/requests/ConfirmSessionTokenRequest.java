package com.knowlink.api.bookings.controllers.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmSessionTokenRequest(
        @NotBlank(message = "Confirmation token is required")
        @Pattern(regexp = "\\d{4}", message = "Confirmation token must be a 4-digit numeric code")
        String token
) {}