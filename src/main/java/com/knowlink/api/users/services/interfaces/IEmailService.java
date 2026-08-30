package com.knowlink.api.users.services.interfaces;

import com.knowlink.api.users.data.models.User;

import java.util.UUID;

public interface IEmailService {

    void sendConfirmAccountEmail(User user, UUID token);

    void sendResendConfirmAccountEmail(User user, UUID token);

    void sendResetPasswordEmail(User user, UUID token);
}