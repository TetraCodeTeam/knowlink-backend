package com.knowlink.api.tutors.services.interfaces;

import java.util.List;
import java.util.UUID;

import com.knowlink.api.auth.controllers.requests.TutorRegistrationRequest;
import com.knowlink.api.auth.controllers.requests.TutorSubjectRequest;
import com.knowlink.api.tutors.controllers.responses.TutorProfileResponse;
import com.knowlink.api.tutors.controllers.responses.TutorSelfProfileResponse;
import com.knowlink.api.tutors.controllers.responses.TutorSubjectResponse;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSearchResponse;
import com.knowlink.api.users.data.models.User;

public interface ITutorProfileService {

    TutorProfile createProfile(User user, TutorRegistrationRequest request);

    TutorProfileResponse getTutorProfile(UUID tutorUserId, UUID alumnoUserId);

    TutorSelfProfileResponse getSelfProfile(UUID tutorUserId);

    List<TutorSearchResponse> searchTutor(String query, UUID alumnoUserId);

    /**
     * Registra una nueva materia dictada por el tutor autenticado
     * (tutorUserId). La materia se busca/crea dentro de la carrera del
     * propio tutor.
     */
    TutorSubjectResponse createTutorSubject(UUID tutorUserId, TutorSubjectRequest request);
}
