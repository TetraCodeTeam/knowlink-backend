package com.knowlink.api.email;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gmail")
@Slf4j
public class EmailProperties {

    private String clientId;
    private String clientSecret;
    private String refreshToken;
    private String senderAddress;

    @PostConstruct
    public void validate() {
        if (clientId == null || clientId.isBlank()) {
            log.warn("GMAIL_CLIENT_ID is not configured. Email sending via Gmail API will be unavailable.");
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            log.warn("GMAIL_CLIENT_SECRET is not configured. Email sending via Gmail API will be unavailable.");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("GMAIL_REFRESH_TOKEN is not configured. Email sending via Gmail API will be unavailable.");
        }
        if (senderAddress == null || senderAddress.isBlank()) {
            log.warn("GMAIL_SENDER_ADDRESS is not configured. Email sending via Gmail API will be unavailable.");
        }
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && refreshToken != null && !refreshToken.isBlank()
                && senderAddress != null && !senderAddress.isBlank();
    }
}
