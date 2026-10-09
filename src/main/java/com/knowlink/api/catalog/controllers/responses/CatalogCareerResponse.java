package com.knowlink.api.catalog.controllers.responses;

import com.knowlink.api.catalog.data.enums.CareerType;

import java.util.UUID;

public record CatalogCareerResponse(UUID careerId, String name, CareerType type) {}