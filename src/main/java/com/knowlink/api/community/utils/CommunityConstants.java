package com.knowlink.api.community.utils;

public final class CommunityConstants {

    private CommunityConstants() {
    }

    public static final int MAX_TITLE_LENGTH = 150;
    public static final int HELP_REQUEST_MAX_CONTENT_LENGTH = 500;
    public static final int SHARED_CONTENT_MAX_CONTENT_LENGTH = 2000;
    public static final int REPLY_MAX_CONTENT_LENGTH = 2000;
    public static final int BOOKING_QUESTION_MAX_LENGTH = 150;
    public static final String DEFAULT_BOOKING_QUESTION = "¿Necesitás más ayuda con este tema?";

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 50;
    public static final int FEATURED_EXCERPT_LENGTH = 240;

    public static final String STORAGE_FOLDER = "community";
    public static final int SIGNED_URL_EXPIRATION_SECONDS = 3600;

    public static final String REPLY_NOTIFICATION_TITLE = "Nueva respuesta en tu publicación";
    public static final int NOTIFICATION_POST_TITLE_LENGTH = 100;

    // Ranking mensual de tutores
    public static final int PODIUM_SIZE = 3;
    public static final int TOP_RANKING_SIZE = 10;
    public static final int RANKING_SUBJECTS_LIMIT = 4;
    public static final double BEST_ANSWER_WEIGHT = 10.0;
    public static final double HELPFUL_MARK_WEIGHT = 2.0;
    public static final double AVERAGE_RATING_WEIGHT = 5.0;
}
