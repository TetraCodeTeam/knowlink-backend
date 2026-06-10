package com.knowlink.api.auth.controllers.implementations;

import com.knowlink.api.auth.controllers.interfaces.IAuthController;
import com.knowlink.api.auth.controllers.requests.LoginRequest;
import com.knowlink.api.auth.controllers.requests.RegisterRequest;
import com.knowlink.api.auth.controllers.responses.AuthResponse;
import com.knowlink.api.auth.services.interfaces.IAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthControllerImpl implements IAuthController {

    private final IAuthService authService;

    @Override
    public ResponseEntity<AuthResponse> login(LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Override
    public void register(RegisterRequest request) {
        authService.register(request);
    }
}
