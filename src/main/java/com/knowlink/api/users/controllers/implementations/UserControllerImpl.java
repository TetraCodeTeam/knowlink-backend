package com.knowlink.api.users.controllers.implementations;

import com.knowlink.api.users.controllers.interfaces.IUserController;
import com.knowlink.api.users.controllers.requests.ConfirmTokenRequest;
import com.knowlink.api.users.controllers.requests.EmailRequest;
import com.knowlink.api.users.controllers.requests.ResetPasswordRequest;
import com.knowlink.api.users.controllers.requests.UpdateUserRequest;
import com.knowlink.api.users.controllers.responses.UserResponse;
import com.knowlink.api.users.data.mappers.UserMapper;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.services.interfaces.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UserControllerImpl implements IUserController {

    private final IUserService userService;
    private final UserMapper userMapper;

    @Override
    public void checkAvailability(String email, String dni) {
        userService.checkAvailability(email, dni);
    }

    @Override
    public UserResponse getUserById(UUID userId) {
        User user = userService.findByIdOrThrowException(userId);
        return userMapper.toResponse(user);
    }

    @Override
    public UserResponse updateUser(UUID userId, UpdateUserRequest request) {
        User user = userService.updateUser(userId, request);
        return userMapper.toResponse(user);
    }

    @Override
    public void verifyAccount(UUID userId, ConfirmTokenRequest confirmTokenRequest) {
        userService.verifyNewUser(userId, confirmTokenRequest.token());
    }

    @Override
    public void resendConfirmationEmail(EmailRequest emailRequest) {
        userService.resendConfirmationEmail(emailRequest.email());
    }

    @Override
    public void resetPassword(ResetPasswordRequest resetPasswordRequest) {
        userService.resetPassword(resetPasswordRequest.token(), resetPasswordRequest.newPassword(),
                resetPasswordRequest.confirmNewPassword());
    }

    @Override
    public void sendResetPasswordEmail(EmailRequest emailRequest) {
        userService.sendResetPasswordEmail(emailRequest.email());
    }
}
