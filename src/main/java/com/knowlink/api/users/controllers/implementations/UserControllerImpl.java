package com.knowlink.api.users.controllers.implementations;

import com.knowlink.api.users.controllers.interfaces.IUserController;
import com.knowlink.api.users.controllers.requests.ConfirmTokenRequest;
import com.knowlink.api.users.controllers.requests.DeleteAccountRequest;
import com.knowlink.api.users.controllers.requests.EmailRequest;
import com.knowlink.api.users.controllers.requests.ResetPasswordRequest;
import com.knowlink.api.users.controllers.responses.UserResponse;
import com.knowlink.api.users.data.mappers.UserMapper;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.services.interfaces.IAccountDeletionService;
import com.knowlink.api.users.services.interfaces.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UserControllerImpl implements IUserController {

    private final IUserService userService;
    private final IAccountDeletionService accountDeletionService;
    private final UserMapper userMapper;

    @Override
    public void checkAvailability(String email, String dni) {
        userService.checkAvailability(email, dni);
    }

    @Override
    public UserResponse getUserById(UUID userId, UserPrincipal principal) {
        User user = userService.findVisibleByIdOrThrowException(userId, principal);
        return userMapper.toResponse(user);
    }

    @Override
    public void deactivateMyAccount(UserPrincipal principal) {
        userService.deactivateAccount(principal.getUser().getUserId());
    }
    @Override
    public void deleteMyAccount(UserPrincipal principal, DeleteAccountRequest request) {
        accountDeletionService.deleteAccount(principal.getUser().getUserId(), request.password());
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
