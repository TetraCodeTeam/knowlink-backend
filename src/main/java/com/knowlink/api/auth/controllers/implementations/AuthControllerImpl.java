package com.knowlink.api.auth.controllers.implementations;

import com.knowlink.api.auth.controllers.interfaces.IAuthController;
import com.knowlink.api.auth.controllers.requests.LoginRequest;
import com.knowlink.api.auth.controllers.requests.ReactivateAccountRequest;
import com.knowlink.api.auth.controllers.requests.StudentRegistrationRequest;
import com.knowlink.api.auth.controllers.requests.TutorRegistrationRequest;
import com.knowlink.api.auth.controllers.responses.AuthResponse;
import com.knowlink.api.auth.services.interfaces.IAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class AuthControllerImpl implements IAuthController {

    private final IAuthService authService;

    @Override
    public AuthResponse login(LoginRequest request) {
        return this.authService.login(request);
    }

    @Override
    public AuthResponse reactivate(ReactivateAccountRequest request) {
        return this.authService.reactivate(request);
    }

    @Override
    public void registerStudent(StudentRegistrationRequest request, MultipartFile profilePicture) {
        this.authService.registerStudent(request, profilePicture);
    }

    @Override
    public void registerTutor(TutorRegistrationRequest request, MultipartFile profilePicture) {
        this.authService.registerTutor(request, profilePicture);
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