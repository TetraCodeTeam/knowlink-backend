package com.knowlink.api.auth.controllers.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record TutorRegistrationRequest(

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
        String dni,

        @NotBlank(message = "Phone number is required")
        String phoneNumber,

        @NotBlank(message = "Career is required")
        String career,

        String institutionalId,

        String profilePictureUrl,

        @Size(max = 300, message = "Biography cannot exceed 300 characters")
        String biography,

        String address,

        @NotEmpty(message = "At least one subject is required")
        @Valid
        List<TutorSubjectRequest> subjects
) {}
