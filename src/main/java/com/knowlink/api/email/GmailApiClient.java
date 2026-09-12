package com.knowlink.api.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class GmailApiClient {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String SEND_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

    private final WebClient.Builder webClientBuilder;
    private final EmailProperties emailProperties;

    private String cachedAccessToken;
    private Instant tokenExpiration;

    public void sendEmail(String to, String subject, String htmlBody) {
        if (!emailProperties.isConfigured()) {
            log.warn("Gmail API is not configured. Skipping email send to {}", to);
            return;
        }

        String accessToken = getAccessToken();
        if (accessToken == null) {
            log.error("Failed to obtain Gmail API access token. Cannot send email to {}", to);
            return;
        }

        String message = buildRfc2822Message(to, subject, htmlBody);
        String rawMessage = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(message.getBytes(StandardCharsets.UTF_8));

        Map<String, String> body = Map.of("raw", rawMessage);

        try {
            webClientBuilder.build()
                    .post()
                    .uri(SEND_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("Email sent successfully to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {} via Gmail API: {}", to, e.getMessage());
        }
    }

    private synchronized String getAccessToken() {
        if (cachedAccessToken != null && tokenExpiration != null && Instant.now().isBefore(tokenExpiration)) {
            return cachedAccessToken;
        }
        return refreshAccessToken();
    }

    private String refreshAccessToken() {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(TOKEN_URL)
                    .queryParam("client_id", emailProperties.getClientId())
                    .queryParam("client_secret", emailProperties.getClientSecret())
                    .queryParam("refresh_token", emailProperties.getRefreshToken())
                    .queryParam("grant_type", "refresh_token")
                    .toUriString();

            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClientBuilder.build()
                    .post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("access_token")) {
                cachedAccessToken = (String) response.get("access_token");
                int expiresIn = (int) response.getOrDefault("expires_in", 3600);
                tokenExpiration = Instant.now().plusSeconds(expiresIn - 300);
                log.info("Gmail API access token refreshed successfully");
                return cachedAccessToken;
            }

            log.error("Gmail API token response missing access_token: {}", response);
            return null;
        } catch (Exception e) {
            log.error("Failed to refresh Gmail API access token: {}", e.getMessage());
            return null;
        }
    }

    private String buildRfc2822Message(String to, String subject, String htmlBody) {
        return "From: " + emailProperties.getSenderAddress() + "\r\n"
                + "To: " + to + "\r\n"
                + "Subject: =?UTF-8?B?"
                + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8))
                + "?=\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n"
                + "\r\n"
                + Base64.getEncoder().encodeToString(htmlBody.getBytes(StandardCharsets.UTF_8));
    }
}
