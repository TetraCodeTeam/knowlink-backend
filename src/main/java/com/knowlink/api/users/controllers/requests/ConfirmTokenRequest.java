package com.knowlink.api.users.controllers.requests;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record ConfirmTokenRequest (
        @NotNull(message = "El token es obligatorio")
        UUID token
) {}
