package com.knowlink.api.students.validations;

import com.knowlink.api.users.data.models.User;

public interface IStudentProfileValidationService {
    void ifStudentProfileAlreadyExistsThrowException(User user);
}