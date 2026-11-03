package com.knowlink.api.auth.controllers.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
        @Size(max = 100, message = "First name cannot exceed 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name cannot exceed 100 characters")
        String lastName,

        @NotBlank(message = "DNI is required")
        @Pattern(regexp = "^\\d{7,8}$", message = "DNI must be a valid 7 or 8 digit number")
        String dni,

        @NotBlank(message = "Phone number is required")
        @Size(max = 30, message = "Phone number cannot exceed 30 characters")
        String phoneNumber,

        @NotBlank(message = "Career is required")
        String career,

        @Size(max = 50, message = "Institutional ID cannot exceed 50 characters")
        String institutionalId,

        String profilePictureUrl
) {}