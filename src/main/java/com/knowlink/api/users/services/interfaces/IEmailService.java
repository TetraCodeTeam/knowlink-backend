package com.knowlink.api.users.services.interfaces;

import com.knowlink.api.users.data.models.User;

import java.util.UUID;

public interface IEmailService {

    void sendConfirmAccountEmail(User user, UUID token);

    void sendResendConfirmAccountEmail(User user, UUID token);

    void sendResetPasswordEmail(User user, UUID token);

    /**
     * Sends a business notification email (admin module). Failures must not roll back
     * the originating transaction; callers run after commit.
     */
    void sendAdminNotificationEmail(User recipient, String subject, String heading, String bodyText);
}