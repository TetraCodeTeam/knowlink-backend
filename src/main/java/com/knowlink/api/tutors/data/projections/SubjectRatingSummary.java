package com.knowlink.api.tutors.data.projections;

import java.util.UUID;

public interface SubjectRatingSummary {

    UUID getSubjectId();

    String getSubjectName();

    Double getAverageScore();

    Long getRatingCount();
}
