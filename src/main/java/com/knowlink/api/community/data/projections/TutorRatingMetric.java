package com.knowlink.api.community.data.projections;

import java.util.UUID;

public interface TutorRatingMetric {

    UUID getTutorId();

    Double getAverageScore();

    long getRatingCount();
}
