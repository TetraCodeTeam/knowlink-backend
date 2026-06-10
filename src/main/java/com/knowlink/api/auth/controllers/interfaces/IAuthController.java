package com.knowlink.api.auth.controllers.interfaces;

import com.knowlink.api.auth.controllers.requests.LoginRequest;
import com.knowlink.api.auth.controllers.requests.RegisterRequest;
import com.knowlink.api.auth.controllers.responses.AuthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.springframework.http.HttpStatus.CREATED;

@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Autenticación y registro de usuarios")
public interface IAuthController {

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión")
    ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request);

    @PostMapping("/register")
    @Operation(summary = "Registrar nuevo usuario")
    @org.springframework.web.bind.annotation.ResponseStatus(CREATED)
    void register(@RequestBody @Valid RegisterRequest request);
}
