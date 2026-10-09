package com.knowlink.api.catalog.controllers.requests;

import com.knowlink.api.catalog.data.enums.AssociationMode;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record UpdateSubjectCareersRequest(

        @NotEmpty(message = "At least one career is required")
        List<UUID> careerIds,

        AssociationMode mode
) {}