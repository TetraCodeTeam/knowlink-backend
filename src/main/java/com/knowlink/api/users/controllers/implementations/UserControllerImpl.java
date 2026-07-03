package com.knowlink.api.users.controllers.implementations;

import com.knowlink.api.users.controllers.interfaces.IUserController;
import com.knowlink.api.users.controllers.requests.ConfirmTokenRequest;
import com.knowlink.api.users.controllers.requests.EmailRequest;
import com.knowlink.api.users.controllers.requests.ResetPasswordRequest;
import com.knowlink.api.users.services.interfaces.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UserControllerImpl implements IUserController {

    private final IUserService userService;

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
        userService.resetPassword(resetPasswordRequest.token(), resetPasswordRequest.newPassword(), resetPasswordRequest.confirmNewPassword());
    }

    @Override
    public void sendResetPasswordEmail(EmailRequest emailRequest) {
        userService.sendResetPasswordEmail(emailRequest.email());
    }
}
