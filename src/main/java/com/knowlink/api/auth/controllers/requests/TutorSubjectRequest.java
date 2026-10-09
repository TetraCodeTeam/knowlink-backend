package com.knowlink.api.auth.controllers.requests;

import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record TutorSubjectRequest(

        @NotNull(message = "Subject is required")
        UUID subjectId,

        @NotNull(message = "Modality is required")
        Modality modality,

        @NotNull(message = "Compensation type is required")
        CompensationType compensationType,

        @DecimalMin(value = "0", message = "Price cannot be negative")
        BigDecimal pricePerHour
) {}