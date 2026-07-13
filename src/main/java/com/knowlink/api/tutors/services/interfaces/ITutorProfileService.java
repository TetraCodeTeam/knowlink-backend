package com.knowlink.api.tutors.services.interfaces;

import java.util.List;
import java.util.UUID;

import com.knowlink.api.auth.controllers.requests.TutorRegistrationRequest;
import com.knowlink.api.tutors.controllers.responses.TutorProfileResponse;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSearchResponse;
import com.knowlink.api.users.data.models.User;

public interface ITutorProfileService {

    TutorProfile createProfile(User user, TutorRegistrationRequest request);

    TutorProfileResponse getTutorProfile(UUID tutorUserId, UUID alumnoUserId);

    List<TutorSearchResponse> searchTutor(String query);
}
