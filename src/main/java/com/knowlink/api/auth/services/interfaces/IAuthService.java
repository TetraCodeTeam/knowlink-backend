package com.knowlink.api.auth.services.interfaces;

import com.knowlink.api.auth.controllers.requests.LoginRequest;
import com.knowlink.api.auth.controllers.requests.UserRegistrationRequest;
import com.knowlink.api.auth.controllers.responses.AuthResponse;

public interface IAuthService {
    AuthResponse login(LoginRequest request);
    void register(UserRegistrationRequest request);
}
