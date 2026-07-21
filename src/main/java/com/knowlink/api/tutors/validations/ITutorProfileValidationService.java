package com.knowlink.api.tutors.validations;

import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.users.data.models.User;

import java.util.UUID;

public interface ITutorProfileValidationService {
    void ifTutorProfileAlreadyExistsThrowException(User user);
    TutorProfile findTutorProfileOrThrowException(UUID tutorUserId);
}