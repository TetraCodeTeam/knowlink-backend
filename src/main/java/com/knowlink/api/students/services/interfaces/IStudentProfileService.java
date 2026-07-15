package com.knowlink.api.students.services.interfaces;

import com.knowlink.api.auth.controllers.requests.StudentRegistrationRequest;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.users.data.models.User;

public interface IStudentProfileService {
    StudentProfile createProfile(User user, StudentRegistrationRequest request);
}