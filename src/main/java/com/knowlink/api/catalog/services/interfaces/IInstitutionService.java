package com.knowlink.api.catalog.services.interfaces;

import com.knowlink.api.catalog.data.models.Institution;

import java.util.List;
import java.util.UUID;

public interface IInstitutionService {

    Institution createInstitution(String name);

    List<Institution> findAll();

    Institution findOrThrow(UUID institutionId);
}