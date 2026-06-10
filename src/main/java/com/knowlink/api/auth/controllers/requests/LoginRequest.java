package com.knowlink.api.auth.controllers.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "El email es requerido")
        @Email(message = "El email no es válido")
        String email,

        @NotBlank(message = "La contraseña es requerida")
        String password
) {}
