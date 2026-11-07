package com.knowlink.api.community.controllers.responses;

import java.util.UUID;

public record CommunityCareerResponse(
        UUID careerId,
        String name
) {
}