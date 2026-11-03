package com.knowlink.api.auth.validations;

import com.knowlink.api.security.enums.Role;
import com.knowlink.api.users.data.models.User;

public interface IAuthValidationService {
    void ifUserLacksProfileForTargetRoleThrowException(User user, Role targetRole);

    /**
     * Reveals the deactivated state ONLY when the password is correct (no account enumeration).
     */
    void ifDeactivatedAccountThrowExceptionOrBadCredentials(User user, String rawPassword);
}