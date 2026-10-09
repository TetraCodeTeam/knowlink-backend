package com.knowlink.api.catalog.services.interfaces;

import com.knowlink.api.catalog.controllers.responses.CatalogSubjectResponse;
import com.knowlink.api.catalog.data.enums.AssociationMode;
import com.knowlink.api.tutors.data.models.Career;
import com.knowlink.api.tutors.data.models.Subject;

import java.util.List;
import java.util.UUID;

public interface ICatalogSubjectService {

    CatalogSubjectResponse createSubject(UUID institutionId, String name, List<UUID> careerIds);

    CatalogSubjectResponse updateSubjectCareers(UUID subjectId, List<UUID> careerIds, AssociationMode mode);

    List<CatalogSubjectResponse> findByInstitution(UUID institutionId);

    List<CatalogSubjectResponse> findVisibleByInstitutionAndCareer(UUID institutionId, UUID careerId);

    Subject findSubjectTeachableInCareerOrThrow(UUID subjectId, Career career);
}