package com.knowlink.api.users.services.interfaces;

import java.util.UUID;

public interface IUserProfileLookupService {
    boolean hasTutorProfile(UUID userId);
    boolean hasStudentProfile(UUID userId);
}