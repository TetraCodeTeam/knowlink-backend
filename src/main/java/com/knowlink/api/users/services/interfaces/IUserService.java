package com.knowlink.api.users.services.interfaces;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.auth.controllers.requests.StudentRegistrationRequest;
import com.knowlink.api.auth.controllers.requests.TutorRegistrationRequest;
import com.knowlink.api.users.data.models.User;

import java.util.UUID;

public interface IUserService {

    User saveStudentUser(StudentRegistrationRequest request);

    User saveTutorUser(TutorRegistrationRequest request);

    void checkAvailability(String email, String dni);

    User findByIdOrThrowException(UUID userId);

    User updatePersonalData(UUID userId, String firstName, String lastName, String dni, String phoneNumber);

    User findUserByEmailOrThrowException(String email);

    void verifyNewUser(UUID userId, UUID token);

    void resendConfirmationEmail(String email);

    User updateUserRole(UUID userId, Role role);

    void resetPassword(UUID token, String newPassword, String confirmNewPassword);

    void sendResetPasswordEmail(String email);

    User lockForUpdateOrThrowException(UUID userId);
}
