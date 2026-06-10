package com.knowlink.api.users.controllers.implementations;

import com.knowlink.api.users.controllers.interfaces.IUserController;
import com.knowlink.api.users.controllers.requests.UpdateUserRequest;
import com.knowlink.api.users.controllers.responses.UserResponse;
import com.knowlink.api.users.services.interfaces.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UserControllerImpl implements IUserController {

    private final IUserService userService;

    @Override
    public ResponseEntity<UserResponse> getUserById(UUID userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserResponse>> getAllUsers(Pageable pageable) {
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    @Override
    public ResponseEntity<UserResponse> updateUser(UUID userId, UpdateUserRequest request) {
        return ResponseEntity.ok(userService.updateUser(userId, request));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(UUID userId) {
        userService.deleteUser(userId);
    }
}
