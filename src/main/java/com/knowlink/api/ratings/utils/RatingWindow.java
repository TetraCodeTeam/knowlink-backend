package com.knowlink.api.ratings.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Single source of truth for the post-session rating window.
 * The window starts at the later of the session end and the confirmation time.
 */
public final class RatingWindow {

    private RatingWindow() {
    }

    public static LocalDateTime deadline(LocalDate sessionDate, LocalTime endTime, LocalDateTime confirmedAt) {
        LocalDateTime sessionEnd = LocalDateTime.of(sessionDate, endTime);
        LocalDateTime start = (confirmedAt != null && confirmedAt.isAfter(sessionEnd))
                ? confirmedAt
                : sessionEnd;
        return start.plus(RatingConstants.RATING_WINDOW);
    }

    public static boolean isOpen(LocalDateTime deadline, LocalDateTime now) {
        return !now.isAfter(deadline);
    }
}