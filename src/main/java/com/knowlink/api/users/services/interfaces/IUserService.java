package com.knowlink.api.users.services.interfaces;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.auth.controllers.requests.StudentRegistrationRequest;
import com.knowlink.api.auth.controllers.requests.TutorRegistrationRequest;
import com.knowlink.api.users.data.models.User;

import java.util.Optional;
import java.util.UUID;

public interface IUserService {

    User saveStudentUser(StudentRegistrationRequest request);

    User saveTutorUser(TutorRegistrationRequest request);

    void checkAvailability(String email, String dni);

    User findByIdOrThrowException(UUID userId);

    User updatePersonalData(UUID userId, String firstName, String lastName, String dni, String phoneNumber);

    User findUserByEmailOrThrowException(String email);

    Optional<User> findOptionalByEmail(String email);

    void verifyNewUser(UUID userId, UUID token);

    void resendConfirmationEmail(String email);

    User updateUserRole(UUID userId, Role role);

    void resetPassword(String token, String newPassword, String confirmNewPassword);

    void sendResetPasswordEmail(String email);

    User lockForUpdateOrThrowException(UUID userId);

    void deactivateAccount(UUID userId);

    User reactivateAccount(UUID userId);

    User findVisibleByIdOrThrowException(UUID targetId, UserPrincipal requester);

    /** Overwrites the personal data of the account and leaves an anonymized tombstone (US-24). */
    User anonymizeForDeletion(User user);
}