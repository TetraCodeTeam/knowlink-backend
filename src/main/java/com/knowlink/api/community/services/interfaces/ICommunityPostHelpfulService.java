package com.knowlink.api.community.services.interfaces;

import com.knowlink.api.community.controllers.responses.PostHelpfulMarkResponse;

import java.util.UUID;

/** "Esto me sirvió" sobre un aporte (publicación {@code SHARED_CONTENT}). Ambas operaciones son idempotentes. */
public interface ICommunityPostHelpfulService {

    PostHelpfulMarkResponse markHelpful(UUID userId, UUID postId);

    PostHelpfulMarkResponse unmarkHelpful(UUID userId, UUID postId);
}
