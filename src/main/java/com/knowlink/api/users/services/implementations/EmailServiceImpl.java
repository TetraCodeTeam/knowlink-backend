package com.knowlink.api.users.services.implementations;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.knowlink.api.email.EmailService;
import com.knowlink.api.users.builder.EmailBuilder;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.services.interfaces.IEmailService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements IEmailService {

    private final EmailBuilder emailBuilder;
    private final EmailService emailService;

    @Override
    public void sendConfirmAccountEmail(User user, UUID token) {
        String htmlContent = emailBuilder.buildConfirmAccount(user, token);
        sendEmail(user.getEmail(), "Confirmá tu registro en KnowLink ✔", htmlContent);
    }

    @Override
    public void sendResendConfirmAccountEmail(User user, UUID token) {
        String htmlContent = emailBuilder.buildResendConfirmAccount(user, token);
        sendEmail(user.getEmail(), "Nuevo enlace de confirmación en KnowLink ✔", htmlContent);
    }

    @Override
    public void sendResetPasswordEmail(User user, UUID token) {
        String htmlContent = emailBuilder.buildResetPassword(user, token);
        sendEmail(user.getEmail(), "Restablecé tu contraseña ✔", htmlContent);
    }

    private void sendEmail(String to, String subject, String htmlContent) {
        try {
            emailService.enviarCorreo(to, subject, htmlContent);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
