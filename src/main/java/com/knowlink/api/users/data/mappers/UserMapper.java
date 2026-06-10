package com.knowlink.api.users.data.mappers;

import com.knowlink.api.users.controllers.responses.UserResponse;
import com.knowlink.api.users.data.models.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getUserId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getProfilePicture(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getCreatedAt()
        );
    }
}
