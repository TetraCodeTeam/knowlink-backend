package com.knowlink.api.users.controllers.requests;

import jakarta.validation.constraints.Email;

public record EmailRequest (
        @Email(message = "Invalid email format")
        String email
) {}
