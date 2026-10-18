package com.knowlink.api.tutors.support.controllers.responses;

import java.util.List;

public record MaterialReportingPolicyResponse(
        List<String> acceptedFormats,
        int maxDescriptionLength,
        int maxDefenseAttachments,
        long maxDefenseAttachmentSizeBytes,
        String permittedContentPolicy,
        List<ReasonPolicy> reportReasons,
        String reviewProcess,
        String upheldOutcome,
        String dismissedOutcome,
        String reporterNotification) {

    public record ReasonPolicy(String code, String label, String description) {}
}
