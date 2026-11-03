package com.knowlink.api.users.controllers.requests;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(
        @NotBlank(message = "La contraseña es requerida") String password
) {}