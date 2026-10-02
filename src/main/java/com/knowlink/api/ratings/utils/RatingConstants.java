package com.knowlink.api.ratings.utils;

import java.time.Duration;

public final class RatingConstants {

    private RatingConstants() {
    }

    public static final Duration BLIND_REVIEW_WINDOW = Duration.ofHours(24);
    public static final int MAX_COMMENT_LENGTH = 2_000;
}