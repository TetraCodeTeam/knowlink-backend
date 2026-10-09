package com.knowlink.api.auth.controllers.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record StudentRegistrationRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[^a-zA-Z0-9]).+$",
                message = "Password does not meet minimum security requirements"
        )
        String password,

        @NotBlank(message = "Password confirmation is required")
        String confirmPassword,

        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        @NotBlank(message = "DNI is required")
        @Pattern(regexp = "^\\d{7,8}$", message = "DNI must be a valid 7 or 8 digit number")
        String dni,

        @NotBlank(message = "Phone number is required")
        String phoneNumber,

        @NotNull(message = "Institution is required")
        UUID institutionId,

        @NotNull(message = "Career is required")
        UUID careerId,

        String institutionalId,

        String profilePictureUrl
) {}