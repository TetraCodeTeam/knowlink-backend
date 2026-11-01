package com.knowlink.api.tutors.controllers.requests;

import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateTutorSubjectRequest(

        @NotNull(message = "Modality is required")
        Modality modality,

        @NotNull(message = "Compensation type is required")
        CompensationType compensationType,

        @Digits(integer = 8, fraction = 2, message = "Price must have at most 8 integer digits and 2 decimals")
        BigDecimal pricePerHour
) {}
