package com.knowlink.api.catalog.controllers.implementations;

import com.knowlink.api.catalog.controllers.interfaces.ICatalogSubjectController;
import com.knowlink.api.catalog.controllers.requests.CreateSubjectRequest;
import com.knowlink.api.catalog.controllers.requests.UpdateSubjectCareersRequest;
import com.knowlink.api.catalog.controllers.responses.CatalogSubjectResponse;
import com.knowlink.api.catalog.services.interfaces.ICatalogSubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CatalogSubjectControllerImpl implements ICatalogSubjectController {

    private final ICatalogSubjectService catalogSubjectService;

    @Override
    public CatalogSubjectResponse createSubject(UUID institutionId, CreateSubjectRequest request) {
        return catalogSubjectService.createSubject(institutionId, request.name(), request.careerIds());
    }

    @Override
    public CatalogSubjectResponse updateSubjectCareers(UUID subjectId, UpdateSubjectCareersRequest request) {
        return catalogSubjectService.updateSubjectCareers(subjectId, request.careerIds(), request.mode());
    }

    @Override
    public List<CatalogSubjectResponse> getSubjects(UUID institutionId, UUID careerId) {
        if (careerId == null) {
            return catalogSubjectService.findByInstitution(institutionId);
        }
        return catalogSubjectService.findVisibleByInstitutionAndCareer(institutionId, careerId);
    }
}