package com.knowlink.api.auth.controllers.requests;

import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TutorSubjectRequest(

        @NotBlank(message = "Subject name is required")
        String subjectName,

        @NotNull(message = "Modality is required")
        Modality modality,

        @NotNull(message = "Compensation type is required")
        CompensationType compensationType,

        @DecimalMin(value = "0.01", inclusive = true, message = "Price must be greater than zero")
        BigDecimal pricePerHour
) {}
