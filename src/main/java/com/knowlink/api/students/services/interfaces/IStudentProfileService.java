package com.knowlink.api.students.services.interfaces;

import com.knowlink.api.auth.controllers.requests.StudentRegistrationRequest;
import com.knowlink.api.students.controllers.requests.ActivateTutorRoleRequest;
import com.knowlink.api.students.controllers.responses.ActivateTutorRoleResponse;
import com.knowlink.api.students.controllers.responses.StudentSelfProfileResponse;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.users.data.models.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface IStudentProfileService {
    StudentProfile createProfile(User user, StudentRegistrationRequest request);

    StudentSelfProfileResponse getSelfProfile(UUID userId);

    StudentSelfProfileResponse uploadProfilePicture(UUID userId, MultipartFile file);

    void syncProfilePicture(UUID userId, String profilePictureUrl);

    StudentProfile createProfileFromTutorData(User user, TutorProfile tutorProfile);

    ActivateTutorRoleResponse activateTutorRole(UUID userId, ActivateTutorRoleRequest request);
}