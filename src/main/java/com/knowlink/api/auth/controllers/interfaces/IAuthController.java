package com.knowlink.api.auth.controllers.interfaces;

import com.knowlink.api.auth.controllers.requests.LoginRequest;
import com.knowlink.api.auth.controllers.requests.UserRegistrationRequest;
import com.knowlink.api.auth.controllers.responses.AuthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;;

@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication and user registration")
public interface IAuthController {

    @PostMapping("/login")
    @Operation(summary = "Login")
    @ResponseStatus(OK)
    AuthResponse login(@RequestBody @Valid LoginRequest request);

    @PostMapping("/register")
    @Operation(summary = "Register new user")
    @ResponseStatus(CREATED)
    void register(@RequestBody @Valid UserRegistrationRequest request);
}
