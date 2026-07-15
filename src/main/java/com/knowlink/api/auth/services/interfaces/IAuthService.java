package com.knowlink.api.auth.services.interfaces;

import com.knowlink.api.auth.controllers.requests.LoginRequest;
import com.knowlink.api.auth.controllers.requests.StudentRegistrationRequest;
import com.knowlink.api.auth.controllers.requests.TutorRegistrationRequest;
import com.knowlink.api.auth.controllers.responses.AuthResponse;

public interface IAuthService {
    AuthResponse login(LoginRequest request);
    void registerStudent(StudentRegistrationRequest request); 
    void registerTutor(TutorRegistrationRequest request);
}
