package com.knowlink.api.users.services.interfaces;

import com.knowlink.api.auth.controllers.requests.LoginRequest;
import com.knowlink.api.auth.controllers.requests.UserRegistrationRequest;
import com.knowlink.api.auth.controllers.responses.AuthResponse;
import com.knowlink.api.users.data.models.User;

import java.util.UUID;

public interface IUserService {
    
    void saveUser(UserRegistrationRequest userRegistrationRequest);

    User findByIdOrThrowException(UUID userId) ;

    User findUserByEmailOrThrowException(String email);

    void verifyNewUser(UUID userId, UUID token);

    void resendConfirmationEmail(String email);

    AuthResponse verifyUser(LoginRequest loginRequest);

    void resetPassword(UUID token, String newPassword, String confirmNewPassword);

    void sendResetPasswordEmail(String email);
}
