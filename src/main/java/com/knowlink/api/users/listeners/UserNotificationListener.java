package com.knowlink.api.users.listeners;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.knowlink.api.users.events.PasswordResetRequestedEvent;
import com.knowlink.api.users.events.ResendConfirmationEvent;
import com.knowlink.api.users.events.UserRegisteredEvent;
import com.knowlink.api.users.services.interfaces.IEmailService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserNotificationListener {

    private final IEmailService emailService;


    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserRegistered(UserRegisteredEvent event) {
        emailService.sendConfirmAccountEmail(event.user(), event.tokenId());
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleResendConfirmation(ResendConfirmationEvent event) {
        emailService.sendResendConfirmAccountEmail(event.user(), event.tokenId());
    }

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePasswordReset(PasswordResetRequestedEvent event) {
        emailService.sendResetPasswordEmail(event.user().getEmail(), event.tokenId());
    }
}
