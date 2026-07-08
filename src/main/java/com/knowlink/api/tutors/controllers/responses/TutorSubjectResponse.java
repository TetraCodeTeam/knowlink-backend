package com.knowlink.api.tutors.controllers.responses;

public record TutorSubjectResponse(
        String subject,
        String description,
        String modality,
        String compensationType,
        Double pricePerHour
) {}