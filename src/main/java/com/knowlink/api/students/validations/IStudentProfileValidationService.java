package com.knowlink.api.students.validations;

import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.users.data.models.User;
import java.util.UUID;

public interface IStudentProfileValidationService {
    void ifStudentProfileAlreadyExistsThrowException(User user);
    StudentProfile getStudentProfileOrThrow(UUID userId);
}