package com.knowlink.api.claims.data.enums;

import com.knowlink.api.security.enums.Role;

import java.util.List;

public enum ClaimReason {
    STUDENT_COULD_NOT_ATTEND,
    TUTOR_COULD_NOT_ATTEND,
    I_COULD_NOT_ATTEND;

    /**
     * Motivos que puede elegir cada rol: el alumno puede reportar al tutor y el tutor al alumno,
     * y ambos pueden reportar su propia ausencia.
     */
    public static List<ClaimReason> allowedFor(Role role) {
        if (role == Role.STUDENT) {
            return List.of(TUTOR_COULD_NOT_ATTEND, I_COULD_NOT_ATTEND);
        }
        if (role == Role.TUTOR) {
            return List.of(STUDENT_COULD_NOT_ATTEND, I_COULD_NOT_ATTEND);
        }
        return List.of();
    }

    public boolean isAllowedFor(Role role) {
        return allowedFor(role).contains(this);
    }
}
