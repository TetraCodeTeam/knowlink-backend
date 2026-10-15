package com.knowlink.api.tutors.controllers.responses;

import java.util.UUID;

public record SubjectAverageResponse(
        UUID subjectId,
        String name,
        Double average,
        Long count) {
}