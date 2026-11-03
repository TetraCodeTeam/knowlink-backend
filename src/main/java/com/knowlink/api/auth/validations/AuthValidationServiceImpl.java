package com.knowlink.api.auth.validations;

import com.knowlink.api.exceptions.custom_exceptions.AccountDeactivatedException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.services.interfaces.IUserProfileLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthValidationServiceImpl implements IAuthValidationService {

    private final IUserProfileLookupService userProfileLookupService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void ifUserLacksProfileForTargetRoleThrowException(User user, Role targetRole) {
        if (targetRole == Role.TUTOR && !userProfileLookupService.hasTutorProfile(user.getUserId())) {
            throw new ValidationException("No tenés un perfil de tutor activo.");
        }
        if (targetRole == Role.STUDENT && !userProfileLookupService.hasStudentProfile(user.getUserId())) {
            throw new ValidationException("No tenés un perfil de alumno activo.");
        }
    }

    @Override
    public void ifDeactivatedAccountThrowExceptionOrBadCredentials(User user, String rawPassword) {
        if (user.getAccountStatus() != AccountStatus.DEACTIVATED) {
            return;
        }
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BadCredentialsException("invalid credentials");
        }
        throw new AccountDeactivatedException(
                String.format("login attempt on deactivated userId '%s'", user.getUserId()));
    }
}