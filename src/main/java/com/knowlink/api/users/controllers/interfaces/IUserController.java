package com.knowlink.api.users.controllers.interfaces;

import com.knowlink.api.users.controllers.requests.ConfirmTokenRequest;
import com.knowlink.api.users.controllers.requests.EmailRequest;
import com.knowlink.api.users.controllers.requests.ResetPasswordRequest;
import com.knowlink.api.users.controllers.requests.UpdateUserRequest;
import com.knowlink.api.users.controllers.responses.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User management")
public interface IUserController {

    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID")
    @ResponseStatus(HttpStatus.OK)
    UserResponse getUserById(@PathVariable UUID userId);

    @PutMapping("/{userId}")
    @Operation(summary = "Update user")
    @ResponseStatus(HttpStatus.OK)
    UserResponse updateUser(@PathVariable UUID userId, @RequestBody @Valid UpdateUserRequest request);

    @PostMapping("/{userId}/verify-account")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void verifyAccount(@PathVariable UUID userId,
                       @RequestBody @Valid ConfirmTokenRequest confirmTokenRequest);

    @PostMapping("/resend-verification-account")
    @ResponseStatus(HttpStatus.OK)
    void resendConfirmationEmail(@RequestBody @Valid EmailRequest emailRequest);

    @PostMapping("/reset-password/email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void sendResetPasswordEmail(@RequestBody @Valid EmailRequest emailRequest);

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@RequestBody @Valid ResetPasswordRequest resetPasswordRequest);
}
