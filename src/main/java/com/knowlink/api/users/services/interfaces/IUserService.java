package com.knowlink.api.users.services.interfaces;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.auth.controllers.requests.StudentRegistrationRequest;
import com.knowlink.api.auth.controllers.requests.TutorRegistrationRequest;
import com.knowlink.api.users.controllers.requests.UpdateUserRequest;
import com.knowlink.api.users.data.models.User;

import java.util.UUID;

public interface IUserService {

    User saveStudentUser(StudentRegistrationRequest request);

    User saveTutorUser(TutorRegistrationRequest request);

    void checkAvailability(String email, String dni);

    User findByIdOrThrowException(UUID userId);

    User updateUser(UUID userId, UpdateUserRequest request);

    User findUserByEmailOrThrowException(String email);

    void verifyNewUser(UUID userId, UUID token);

    void resendConfirmationEmail(String email);

    User updateUserRole(UUID userId, Role role);

    void resetPassword(UUID token, String newPassword, String confirmNewPassword);

    void sendResetPasswordEmail(String email);
}
