package com.knowlink.api.users.controllers.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static com.knowlink.api.users.utils.Constants.PASSWORD_REGEX;

public record ResetPasswordRequest(
        @NotBlank(message = "Token is required")
        String token,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Pattern(regexp = PASSWORD_REGEX,
                message = "Password does not meet minimum security requirements")
        String newPassword,

        @NotBlank(message = "Password confirmation is required")
        String confirmNewPassword
) {}