package com.knowlink.api.tutors.controllers.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateTutorProfileRequest(

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

        @Size(max = 300, message = "Biography cannot exceed 300 characters")
        String biography,

        @Size(max = 255, message = "Address cannot exceed 255 characters")
        String address
) {}
