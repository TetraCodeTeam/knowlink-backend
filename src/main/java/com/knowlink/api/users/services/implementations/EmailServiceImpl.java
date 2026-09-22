package com.knowlink.api.users.services.implementations;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.knowlink.api.users.builder.EmailBuilder;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.services.interfaces.IEmailService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements IEmailService {

    @Value("${spring.mail.username}")
    private String senderEmail;

    private final JavaMailSender javaMailSender;
    private final EmailBuilder emailBuilder;

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
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            javaMailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }
}