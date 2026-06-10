package com.knowlink.api.users.services.interfaces;

import com.knowlink.api.users.controllers.requests.UpdateUserRequest;
import com.knowlink.api.users.controllers.responses.UserResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface IUserService {
    UserResponse getUserById(UUID userId);
    Page<UserResponse> getAllUsers(Pageable pageable);
    UserResponse updateUser(UUID userId, UpdateUserRequest request);
    void deleteUser(UUID userId);
}
