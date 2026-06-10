package com.knowlink.api.users.services;

import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.users.controllers.responses.UserResponse;
import com.knowlink.api.users.data.enums.UserStatus;
import com.knowlink.api.users.data.mappers.UserMapper;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.repositories.IUserRepository;
import com.knowlink.api.users.services.implementations.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private IUserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private User testUser;
    private UserResponse testResponse;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = User.builder()
                .userId(userId)
                .email("test@knowlink.com")
                .firstName("Test")
                .lastName("User")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        testResponse = new UserResponse(
                userId, "test@knowlink.com", "Test", "User",
                null, "USER", "ACTIVE", testUser.getCreatedAt()
        );
    }

    @Test
    void getUserById_whenUserExists_returnsUserResponse() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userMapper.toResponse(testUser)).thenReturn(testResponse);

        UserResponse result = userService.getUserById(userId);

        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.email()).isEqualTo("test@knowlink.com");
    }

    @Test
    void getUserById_whenUserNotFound_throwsResourceNotFoundException() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
