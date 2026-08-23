package com.knowlink.api.students.services.interfaces;

import com.knowlink.api.auth.controllers.requests.StudentRegistrationRequest;
import com.knowlink.api.students.controllers.responses.StudentSelfProfileResponse;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.users.data.models.User;

import java.util.UUID;

public interface IStudentProfileService {
    StudentProfile createProfile(User user, StudentRegistrationRequest request);

    StudentSelfProfileResponse getSelfProfile(UUID userId);
}