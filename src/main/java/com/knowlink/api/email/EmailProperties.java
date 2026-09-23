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
@ConfigurationProperties(prefix = "brevo")
@Slf4j
public class EmailProperties {

    private String apiKey;
    private String senderAddress;
    private String senderName;

    @PostConstruct
    public void validate() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("BREVO_API_KEY is not configured. Email sending via Brevo will be unavailable.");
        }
        if (senderAddress == null || senderAddress.isBlank()) {
            log.warn("BREVO_SENDER_ADDRESS is not configured. Email sending via Brevo will be unavailable.");
        }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && senderAddress != null && !senderAddress.isBlank();
    }
}
