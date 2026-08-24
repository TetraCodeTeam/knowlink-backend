package com.knowlink.api.auth.controllers.implementations;

import com.knowlink.api.auth.controllers.interfaces.IAuthController;
import com.knowlink.api.auth.controllers.requests.LoginRequest;
import com.knowlink.api.auth.controllers.requests.StudentRegistrationRequest;
import com.knowlink.api.auth.controllers.requests.TutorRegistrationRequest;
import com.knowlink.api.auth.controllers.responses.AuthResponse;
import com.knowlink.api.auth.services.interfaces.IAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthControllerImpl implements IAuthController {

    private final IAuthService authService;

    @Override
    public AuthResponse login(LoginRequest request) {
        return this.authService.login(request);
    }

    @Override
    public void registerStudent(StudentRegistrationRequest request) {
        this.authService.registerStudent(request);
    }

    @Override
    public void registerTutor(TutorRegistrationRequest request) {
        this.authService.registerTutor(request);
    }

    @Override
    public void logout(String authorization) {
        String token = resolveBearerToken(authorization);
        if (token == null) {
            return;
        }
        this.authService.logout(token);
    }

    private String resolveBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7);
    }
}
