package com.knowlink.api.tutors.services.interfaces;

import com.knowlink.api.tutors.data.models.Institution;

import java.util.List;
import java.util.UUID;

public interface IInstitutionService {

    List<Institution> findAll();

    Institution findByIdOrThrowException(UUID institutionId);
}
