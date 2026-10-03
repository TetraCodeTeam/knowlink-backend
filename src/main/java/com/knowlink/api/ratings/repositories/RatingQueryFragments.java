package com.knowlink.api.ratings.repositories;

public final class RatingQueryFragments {

    private RatingQueryFragments() {
    }

    public static final String DEADLINE_ELAPSED = """
            (
                (b.confirmedAt IS NULL OR b.confirmedAt <= :visibleBefore)
                AND (
                    b.sessionDate < :visibleBeforeDate
                    OR (b.sessionDate = :visibleBeforeDate
                        AND b.endTime <= :visibleBeforeTime)
                )
            )
            """;
}