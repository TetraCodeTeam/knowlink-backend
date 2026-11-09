package com.knowlink.api.tutors.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

/**
 * Thresholds for the derived subject verification: a TutorSubject is verified
 * when the average of the ratings that count for it lies inside
 * [minAverage, maxAverage] and at least minRatings ratings were counted.
 *
 * The comparison is done on the unrounded average, so a value stored as 4.50
 * can still be a real average of 4.496 that does not verify.
 */
@ConfigurationProperties(prefix = "knowlink.verification")
public record SubjectVerificationProperties(
        @DefaultValue("4.5") BigDecimal minAverage,
        @DefaultValue("5.0") BigDecimal maxAverage,
        @DefaultValue("1") int minRatings) {
}