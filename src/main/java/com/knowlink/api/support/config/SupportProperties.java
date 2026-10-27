package com.knowlink.api.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "knowlink.support")
public record SupportProperties(
        @DefaultValue("Equipo de Propiedad Intelectual") String materialReviewTeamName,
        @DefaultValue("24") int claimResponseHours,
        @DefaultValue MaterialDefense materialDefense) {

    public record MaterialDefense(
            @DefaultValue("5") int maxAttachments,
            @DefaultValue("10") long maxAttachmentMb) {

        public long maxAttachmentBytes() {
            return maxAttachmentMb * 1024 * 1024;
        }
    }
}
