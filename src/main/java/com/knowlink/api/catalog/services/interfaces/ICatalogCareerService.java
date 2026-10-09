package com.knowlink.api.catalog.services.interfaces;

import com.knowlink.api.catalog.controllers.requests.CreateCareerRequest;
import com.knowlink.api.catalog.controllers.responses.CatalogCareerResponse;
import com.knowlink.api.tutors.data.models.Career;

import java.util.List;
import java.util.UUID;

public interface ICatalogCareerService {

    String SHARED_CAREER_NAME = "Materias Compartidas";

    CatalogCareerResponse createCareer(UUID institutionId, CreateCareerRequest request);

    List<CatalogCareerResponse> findByInstitution(UUID institutionId);

    Career findOrThrow(UUID careerId);

    Career findInInstitutionOrThrow(UUID institutionId, UUID careerId);

    Career findSharedCareerOrThrow(UUID institutionId);
}