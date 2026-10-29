package com.knowlink.api.community.controllers.responses;

import java.util.UUID;

public record CommunitySubjectResponse(
        UUID subjectId,
        String name
) {
}
