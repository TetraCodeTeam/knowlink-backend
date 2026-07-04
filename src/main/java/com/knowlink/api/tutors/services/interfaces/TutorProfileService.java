package com.knowlink.api.tutors.services.interfaces;

import com.knowlink.api.tutors.data.dto.responses.TutorProfileResponse;

import java.util.UUID;

public interface TutorProfileService {
    TutorProfileResponse getTutorProfile(UUID tutorUserId, UUID alumnoUserId);
}
