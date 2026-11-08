package com.knowlink.api.ratings.gate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@ConfigurationProperties(prefix = "rating-gate")
public class RatingGateProperties {

    private static final Logger log = LoggerFactory.getLogger(RatingGateProperties.class);

    private String enforcedFrom;
    private long clearCacheSeconds = 15;
    private LocalDateTime enforcedFromDateTime;

    public RatingGateProperties() {
    }

    public RatingGateProperties(String enforcedFrom, long clearCacheSeconds) {
        setEnforcedFrom(enforcedFrom);
        this.clearCacheSeconds = clearCacheSeconds;
    }

    public String getEnforcedFrom() {
        return enforcedFrom;
    }

    public void setEnforcedFrom(String enforcedFrom) {
        this.enforcedFrom = enforcedFrom;
        this.enforcedFromDateTime = parse(enforcedFrom);
    }

    public long getClearCacheSeconds() {
        return clearCacheSeconds;
    }

    public void setClearCacheSeconds(long clearCacheSeconds) {
        this.clearCacheSeconds = clearCacheSeconds;
    }

    public Optional<LocalDateTime> enforcedFromAsDateTime() {
        return Optional.ofNullable(enforcedFromDateTime);
    }

    public boolean isEnabled() {
        return enforcedFromDateTime != null;
    }

    private static LocalDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException ex) {
            log.error("Invalid rating-gate.enforced-from value '{}': {}", value, ex.getMessage());
            return null;
        }
    }
}
