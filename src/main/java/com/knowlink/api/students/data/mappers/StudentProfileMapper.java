package com.knowlink.api.students.data.mappers;

import com.knowlink.api.auth.controllers.requests.StudentRegistrationRequest;
import com.knowlink.api.students.data.models.StudentProfile;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.users.data.models.User;
import org.springframework.stereotype.Component;

@Component
public class StudentProfileMapper {

    public StudentProfile toEntity(User user, Career career, StudentRegistrationRequest request) {
        return StudentProfile.builder()
                .user(user)
                .career(career)
                .profilePictureUrl(request.profilePictureUrl())
                .institutionalId(request.institutionalId())
                .build();
    }
}