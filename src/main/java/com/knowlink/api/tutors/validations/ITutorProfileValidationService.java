package com.knowlink.api.tutors.validations;

import com.knowlink.api.users.data.models.User;

public interface ITutorProfileValidationService {
    void ifTutorProfileAlreadyExistsThrowException(User user);
}