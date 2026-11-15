package com.knowlink.api.auth.services.interfaces;

import com.knowlink.api.auth.controllers.requests.LoginRequest;
import com.knowlink.api.auth.controllers.requests.ReactivateAccountRequest;
import com.knowlink.api.auth.controllers.requests.StudentRegistrationRequest;
import com.knowlink.api.auth.controllers.requests.TutorRegistrationRequest;
import com.knowlink.api.auth.controllers.responses.AuthResponse;
import org.springframework.web.multipart.MultipartFile;

public interface IAuthService {
    AuthResponse login(LoginRequest request);
    AuthResponse reactivate(ReactivateAccountRequest request);

    void registerStudent(StudentRegistrationRequest request, MultipartFile profilePicture);

    void registerTutor(TutorRegistrationRequest request, MultipartFile profilePicture);

    void logout(String token);
}