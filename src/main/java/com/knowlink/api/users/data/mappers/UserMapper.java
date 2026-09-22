package com.knowlink.api.users.data.mappers;

import com.knowlink.api.auth.controllers.requests.StudentRegistrationRequest;
import com.knowlink.api.auth.controllers.requests.TutorRegistrationRequest;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.users.controllers.responses.UserResponse;
import com.knowlink.api.users.data.models.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getUserId(),
                user.getEmail(),
                user.getRole().name(),
                user.getAccountStatus().name(),
                user.getProfilePictureUrl(),
                user.getCreatedAt()
        );
    }

    public User toStudentUser(StudentRegistrationRequest request) {
        return User.builder()
                .email(request.email())
                .fullName(request.firstName() + " " + request.lastName())
                .dni(request.dni())
                .phoneNumber(request.phoneNumber())
                .role(Role.STUDENT)
                .build();
    }

    public User toTutorUser(TutorRegistrationRequest request) {
        return User.builder()
                .email(request.email())
                .fullName(request.firstName() + " " + request.lastName())
                .dni(request.dni())
                .phoneNumber(request.phoneNumber())
                .role(Role.TUTOR)
                .build();
    }
}