package com.knowlink.api.tutors.services.interfaces;

import com.knowlink.api.tutors.controllers.responses.TutorRatingHistoryResponse;

import java.util.UUID;

public interface ITutorRatingHistoryService {

    TutorRatingHistoryResponse getRatingHistory(UUID tutorId, UUID subjectId, int page, int size);
}