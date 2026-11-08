package com.knowlink.api.users.services.interfaces;

import java.util.Optional;
import java.util.UUID;

public interface IUserProfileLookupService {
    boolean hasTutorProfile(UUID userId);

    boolean hasStudentProfile(UUID userId);

    /**
     * Institution of the user's academic profile: the student profile first,
     * falling back to the tutor profile when the user only has the latter.
     */
    Optional<UUID> findInstitutionIdByUserId(UUID userId);
}
