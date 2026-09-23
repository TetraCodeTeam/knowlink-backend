package com.knowlink.api.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class BrevoApiClient {

    private static final String SEND_URL = "https://api.brevo.com/v3/smtp/email";

    private final WebClient.Builder webClientBuilder;
    private final EmailProperties emailProperties;

    public void sendEmail(String to, String subject, String htmlBody) {
        if (!emailProperties.isConfigured()) {
            log.warn("Brevo is not configured. Skipping email send to {}", to);
            return;
        }

        Map<String, Object> body = Map.of(
                "sender", Map.of(
                        "name", emailProperties.getSenderName(),
                        "email", emailProperties.getSenderAddress()
                ),
                "to", List.of(Map.of(
                        "email", to
                )),
                "subject", subject,
                "htmlContent", htmlBody
        );

        try {
            webClientBuilder.build()
                    .post()
                    .uri(SEND_URL)
                    .header("api-key", emailProperties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("Email sent successfully to {} via Brevo", to);
        } catch (Exception e) {
            log.error("Failed to send email to {} via Brevo: {}", to, e.getMessage());
        }
    }
}
