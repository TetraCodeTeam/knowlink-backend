package com.knowlink.api.bookings.controllers.requests;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record VirtualSessionLinkRequest(
        @NotBlank(message = "Virtual session link is required")
        @URL(message = "Virtual session link must be a valid URL")
        String virtualSessionLink
) {}