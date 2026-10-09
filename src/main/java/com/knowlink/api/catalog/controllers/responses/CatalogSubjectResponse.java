package com.knowlink.api.catalog.controllers.responses;

import java.util.List;
import java.util.UUID;

public record CatalogSubjectResponse(UUID subjectId, String name, boolean isBasic, List<UUID> careerIds) {}