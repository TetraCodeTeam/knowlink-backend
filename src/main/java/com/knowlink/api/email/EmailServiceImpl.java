package com.knowlink.api.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service("brevoEmailService")
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final BrevoApiClient brevoApiClient;

    @Async("taskExecutor")
    @Override
    public void enviarCorreo(String destinatario, String asunto, String cuerpoHtml) {
        try {
            brevoApiClient.sendEmail(destinatario, asunto, cuerpoHtml);
        } catch (Exception e) {
            log.error("Unexpected error sending email to {}: {}", destinatario, e.getMessage());
        }
    }
}
